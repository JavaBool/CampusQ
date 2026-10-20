
package com.campus.canteen.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Controller orchestrating the multi-step payment lifecycle for orders.
 *
 * <p>Payment Flow &amp; Security Architecture:
 * <ul>
 *   <li><b>Step 2 — Bill Generation ({@code POST /payment/bill}):</b> Requires an active user session.
 *       Applies pessimistic row locking ({@code SELECT ... FOR UPDATE}) on the order record to eliminate
 *       race conditions during concurrent payment initialization. Recalculates totals directly from immutable
 *       {@code order_items} snapshots. Provides idempotency: re-requesting a bill for an existing order reuses
 *       the established payment transaction rather than duplicating records.</li>
 *   <li><b>Step 3 — Payment Execution ({@code POST /payment/pay}):</b> Simulates an external payment gateway callback.
 *       <b>Deliberately unauthenticated:</b> External webhooks and return redirects cannot rely on user session state.
 *       Instead, security is enforced via cryptographic 256-bit transaction tokens and atomic SQL status transitions
 *       ({@code WHERE status = 'PENDING'}). Once marked {@code PAID}, transactions cannot be re-executed.</li>
 *   <li><b>Step 4 — Confirmation Display ({@code POST /order/confirmation}):</b> Renders the verified receipt.
 *       Also intentionally unauthenticated by design, verifying authenticity exclusively by confirming that
 *       the {@code orderId} and {@code transactionId} match a record in {@code payments} with status {@code PAID}.</li>
 * </ul>
 */
@Controller
public class PaymentController {

    private final JdbcTemplate jdbcTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public PaymentController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Generates a cryptographically random transaction identifier prefixed with {@code CB-}.
     *
     * @return a unique 256-bit URL-safe Base64 transaction ID
     */
    private String generateTransactionId() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);

        return "CB-" + Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    /**
     * Initializes a pending payment for an existing order and renders the payment bill.
     *
     * <p>Pessimistic locking ({@code FOR UPDATE}) is held on the order row during validation to prevent
     * concurrent requests from creating duplicate payment records. If a transaction has already been
     * initialized for this order, it is reused idempotently.
     *
     * @param orderId the primary key ID of the order to bill
     * @param session the user's HTTP session
     * @param model the view model
     * @return the {@code "payment-bill"} view name, or an error view
     */
    // STEP 2: Create a pending payment and display the bill.
    @PostMapping("/payment/bill")
    @Transactional
    public String generateBill(
            @RequestParam long orderId,
            HttpSession session,
            Model model) {

        Object sessionUserId = session.getAttribute("userId");

        if (!(sessionUserId instanceof Number)) {
            return "redirect:/auth/login?returnUrl=/cart";
        }

        long userId = ((Number) sessionUserId).longValue();

        // Lock the order row exclusively while inspecting or associating its payment
        List<Map<String, Object>> orders = jdbcTemplate.queryForList(
                """
                SELECT id, user_id, payment_id, transaction_id
                FROM orders
                WHERE id = ? AND user_id = ?
                FOR UPDATE
                """,
                orderId,
                userId
        );

        if (orders.isEmpty()) {
            return showError(model, "Order not found.");
        }

        Map<String, Object> order = orders.get(0);

        // Recalculate bill total authoritatively from stored line item snapshots
        Integer itemCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM order_items
                WHERE order_id = ?
                """,
                Integer.class,
                orderId
        );

        if (itemCount == null || itemCount == 0) {
            return showError(model, "This order contains no items.");
        }

        BigDecimal total = jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(
                    SUM(unit_price * quantity),
                    0.00
                )
                FROM order_items
                WHERE order_id = ?
                """,
                BigDecimal.class,
                orderId
        );

        if (total == null || total.signum() <= 0) {
            return showError(model, "Invalid order total.");
        }

        // Idempotency check: Reuse existing transaction on repeated bill requests
        if (order.get("transaction_id") != null) {
            String existingTransaction =
                    order.get("transaction_id").toString();

            List<Map<String, Object>> existingPayments =
                    jdbcTemplate.queryForList(
                            """
                            SELECT id, amount, status
                            FROM payments
                            WHERE transaction_id = ?
                              AND user_id = ?
                            """,
                            existingTransaction,
                            userId
                    );

            if (existingPayments.isEmpty()) {
                return showError(
                        model,
                        "The order's payment record could not be found."
                );
            }

            Map<String, Object> payment = existingPayments.get(0);

            if (((Number) payment.get("id")).longValue()
                    != ((Number) order.get("payment_id")).longValue()) {
                return showError(
                        model,
                        "The order and payment records do not match."
                );
            }

            if (!total.equals(payment.get("amount"))) {
                return showError(
                        model,
                        "The order total does not match its payment."
                );
            }

            return renderPaymentBill(
                    orderId,
                    existingTransaction,
                    (BigDecimal) payment.get("amount"),
                    payment.get("status").toString(),
                    model
            );
        }

        // Create a new PENDING payment record with cryptographic transaction ID
        String transactionId = generateTransactionId();

        jdbcTemplate.update(
                """
                INSERT INTO payments
                    (transaction_id, user_id, amount, status)
                VALUES (?, ?, ?, 'PENDING')
                """,
                transactionId,
                userId,
                total
        );

        Long paymentId = jdbcTemplate.queryForObject(
                "SELECT id FROM payments WHERE transaction_id = ?",
                Long.class,
                transactionId
        );

        // Associate payment with order only if no prior payment was linked
        int updated = jdbcTemplate.update(
                """
                UPDATE orders
                SET payment_id = ?,
                    transaction_id = ?,
                    total_amount = ?
                WHERE id = ?
                  AND user_id = ?
                  AND payment_id IS NULL
                  AND transaction_id IS NULL
                """,
                paymentId,
                transactionId,
                total,
                orderId,
                userId
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Could not associate the payment with the order."
            );
        }

        return renderPaymentBill(
                orderId,
                transactionId,
                total,
                "PENDING",
                model
        );
    }

    /**
     * Helper to render payment-bill template with necessary model attributes.
     */
    private String renderPaymentBill(
            long orderId,
            String transactionId,
            BigDecimal total,
            String status,
            Model model) {

        model.addAttribute("orderId", orderId);
        model.addAttribute("transactionId", transactionId);
        model.addAttribute("total", total);
        model.addAttribute("paymentStatus", status);

        return "payment-bill";
    }

    /**
     * Simulates payment gateway execution, transitioning a PENDING payment to PAID.
     *
     * <p>Non-obvious design choice:
     * This endpoint is <b>intentionally unauthenticated</b> (no session check) to simulate real-world
     * asynchronous payment gateway callbacks / webhooks that originate outside the user's browser session.
     * Atomicity is guaranteed by updating {@code status = 'PAID'} strictly where {@code status = 'PENDING'}.
     *
     * @param transactionId the cryptographic transaction ID
     * @param model the view model
     * @return redirect confirmation view template, or error view
     */
    // STEP 3: Simulated payment. Intentionally no session validation.
    @PostMapping("/payment/pay")
    @Transactional
    public String pay(
            @RequestParam String transactionId,
            Model model) {

        if (transactionId == null || transactionId.isBlank()) {
            return showError(model, "Transaction ID is required.");
        }

        // Atomic transition: Update only if status is strictly PENDING to prevent double-charging or replays
        int updated = jdbcTemplate.update(
                """
                UPDATE payments
                SET status = 'PAID',
                    paid_at = CURRENT_TIMESTAMP
                WHERE transaction_id = ?
                  AND status = 'PENDING'
                """,
                transactionId
        );

        if (updated != 1) {
            // Do not turn FAILED or unknown transactions into PAID.
            return showError(
                    model,
                    "Transaction not found or already processed."
            );
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT o.id AS orderId,
                       p.transaction_id AS transactionId
                FROM payments p
                JOIN orders o ON o.payment_id = p.id
                WHERE p.transaction_id = ?
                  AND o.transaction_id = p.transaction_id
                  AND p.status = 'PAID'
                """,
                transactionId
        );

        if (rows.isEmpty()) {
            throw new IllegalStateException(
                    "Paid transaction is not linked to an order."
            );
        }

        Map<String, Object> row = rows.get(0);

        model.addAttribute("orderId", row.get("orderId"));
        model.addAttribute("transactionId", row.get("transactionId"));

        // This page submits a POST to /order/confirmation.
        return "payment-redirect-confirmation";
    }

    /**
     * Verifies and displays the final order payment confirmation receipt.
     *
     * <p>Non-obvious design choice:
     * Like {@code /payment/pay}, this endpoint is <b>intentionally unauthenticated</b> to handle redirects
     * returning from simulated or external payment gateways. Authenticity is validated through cryptographic
     * matching of {@code orderId}, {@code transactionId}, and verification of {@code p.status = 'PAID'}.
     *
     * @param orderId the order ID
     * @param transactionId the matching transaction ID
     * @param model the view model
     * @return the {@code "order-confirmation"} view on success, or {@code "checkout-error"}
     */
    // STEP 4: Confirm the paid order. Intentionally no session validation.
    @PostMapping("/order/confirmation")
    public String confirmation(
            @RequestParam long orderId,
            @RequestParam String transactionId,
            Model model) {

        // Validate that both order and payment are linked, match the transaction token, and are marked PAID
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT o.id AS orderId,
                       o.total_amount AS total,
                       p.transaction_id AS transactionId,
                       p.status AS paymentStatus,
                       p.paid_at AS paidAt
                FROM orders o
                JOIN payments p ON p.id = o.payment_id
                WHERE o.id = ?
                  AND o.transaction_id = ?
                  AND p.transaction_id = ?
                  AND p.status = 'PAID'
                """,
                orderId,
                transactionId,
                transactionId
        );

        if (rows.isEmpty()) {
            return showError(
                    model,
                    "The order and paid transaction could not be verified."
            );
        }

        model.addAttribute("order", rows.get(0));
        return "order-confirmation";
    }

    private String showError(Model model, String message) {
        model.addAttribute("error", message);
        return "checkout-error";
    }
}
