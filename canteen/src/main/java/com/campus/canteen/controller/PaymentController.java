
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

@Controller
public class PaymentController {

    private final JdbcTemplate jdbcTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public PaymentController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private String generateTransactionId() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);

        return "CB-" + Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

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

        // Lock the order while checking or creating its payment.
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

        // Calculate the bill from the stored item snapshots.
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

        // Reuse the existing transaction on a repeated bill request.
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

        // The total must be positive because payments has a CHECK constraint.
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

    // STEP 3: Simulated payment. Intentionally no session validation.
    @PostMapping("/payment/pay")
    @Transactional
    public String pay(
            @RequestParam String transactionId,
            Model model) {

        if (transactionId == null || transactionId.isBlank()) {
            return showError(model, "Transaction ID is required.");
        }

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

    // STEP 4: Confirm the paid order. Intentionally no session validation.
    @PostMapping("/order/confirmation")
    public String confirmation(
            @RequestParam long orderId,
            @RequestParam String transactionId,
            Model model) {

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
