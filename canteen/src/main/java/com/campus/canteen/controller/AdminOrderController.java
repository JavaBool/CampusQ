
package com.campus.canteen.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;

/**
 * Controller managing the administrative kitchen queue and order fulfillment workflow.
 *
 * <p>Key Architecture &amp; Business Invariants:
 * <ul>
 *   <li><b>Shared Admin Authentication:</b> Reuses {@link AdminController#requireAdminAccess(HttpServletRequest)}
 *       to enforce consistent cookie-based administrative authentication across admin controllers.</li>
 *   <li><b>Paid-Only Queue Filtering:</b> Orders appear in the kitchen fulfillment list only if their associated
 *       payment has reached {@code p.status = 'PAID'}. Unpaid checkouts are excluded from food preparation.</li>
 *   <li><b>Single-Pass Subquery Aggregation:</b> Uses MySQL {@code GROUP_CONCAT} in a correlated subquery to collapse
 *       multiple line items into a single human-readable summary string (e.g. "Masala Dosa × 2, Filter Coffee × 1")
 *       without generating N+1 queries.</li>
 *   <li><b>Priority-Based Queue Ordering:</b> Employs a custom SQL {@code CASE} expression to prioritize active
 *       preparation stages: {@code PLACED} (1) &rarr; {@code PREPARING} (2) &rarr; {@code READY} (3) &rarr;
 *       {@code COMPLETED} (4) &rarr; {@code CANCELLED} (5), followed by chronological FIFO ordering.</li>
 *   <li><b>Database-Enforced State Machine:</b> Status progressions are guarded directly in the atomic SQL {@code UPDATE}
 *       statement to guarantee that invalid state transitions (e.g. going from {@code PLACED} directly to {@code COMPLETED})
 *       are rejected even under concurrent administrative actions.</li>
 * </ul>
 */
@Controller
public class AdminOrderController {

    private final JdbcTemplate jdbcTemplate;
    private final AdminController adminController;

    public AdminOrderController(
            JdbcTemplate jdbcTemplate,
            AdminController adminController) {
        this.jdbcTemplate = jdbcTemplate;
        this.adminController = adminController;
    }

    /**
     * Projection record representing a paid order in the administrative kitchen queue.
     *
     * @param id order ID
     * @param customerName student full name
     * @param rollNumber student academic roll number
     * @param totalAmount aggregate amount paid
     * @param orderStatus current fulfillment state
     * @param transactionId cryptographic transaction identifier
     * @param createdAt timestamp when the order was placed
     * @param items aggregated comma-separated line items summary
     */
    public record PaidOrder(
            long id,
            String customerName,
            String rollNumber,
            BigDecimal totalAmount,
            String orderStatus,
            String transactionId,
            Timestamp createdAt,
            String items
    ) {}

    // ---------------------------------------------------------
    // View all paid orders
    // ---------------------------------------------------------

    /**
     * Renders the administrative order management dashboard containing all paid orders.
     *
     * @param request HTTP request validated for admin credentials
     * @param model Spring MVC view model
     * @return the {@code "admin-orders"} Thymeleaf view name
     * @throws ResponseStatusException 401 Unauthorized if admin cookie is missing or invalid
     */
    @GetMapping("/admin/orders")
    public String paidOrders(
            HttpServletRequest request,
            Model model) {

        // Uses the existing ADMINSESSIONID authentication.
        adminController.requireAdminAccess(request);

        // Retrieve only paid orders, collapsing child line items via GROUP_CONCAT
        List<PaidOrder> orders = jdbcTemplate.query(
                """
                SELECT
                    o.id,
                    u.full_name,
                    u.roll_number,
                    o.total_amount,
                    o.status AS order_status,
                    p.transaction_id,
                    o.created_at,
                    COALESCE(
                        (
                            SELECT GROUP_CONCAT(
                                CONCAT(
                                    oi.product_name,
                                    ' × ',
                                    oi.quantity
                                )
                                ORDER BY oi.id
                                SEPARATOR ', '
                            )
                            FROM order_items oi
                            WHERE oi.order_id = o.id
                        ),
                        ''
                    ) AS items
                FROM orders o
                JOIN users u
                    ON u.id = o.user_id
                JOIN payments p
                    ON p.id = o.payment_id
                WHERE p.status = 'PAID'
                ORDER BY
                    CASE o.status
                        WHEN 'PLACED' THEN 1
                        WHEN 'PREPARING' THEN 2
                        WHEN 'READY' THEN 3
                        WHEN 'COMPLETED' THEN 4
                        WHEN 'CANCELLED' THEN 5
                        ELSE 6
                    END,
                    o.created_at ASC,
                    o.id ASC
                """,
                (rs, rowNum) -> new PaidOrder(
                        rs.getLong("id"),
                        rs.getString("full_name"),
                        rs.getString("roll_number"),
                        rs.getBigDecimal("total_amount"),
                        rs.getString("order_status"),
                        rs.getString("transaction_id"),
                        rs.getTimestamp("created_at"),
                        rs.getString("items")
                )
        );

        model.addAttribute("orders", orders);

        return "admin-orders";
    }

    // ---------------------------------------------------------
    // Update paid order status
    // ---------------------------------------------------------

    /**
     * Advances an order through the fulfillment state machine.
     *
     * <p>Permitted transitions:
     * <ul>
     *   <li>{@code PLACED} &rarr; {@code PREPARING} or {@code CANCELLED}</li>
     *   <li>{@code PREPARING} &rarr; {@code READY} or {@code CANCELLED}</li>
     *   <li>{@code READY} &rarr; {@code COMPLETED} or {@code CANCELLED}</li>
     * </ul>
     *
     * @param orderId the primary key of the order
     * @param status the destination status
     * @param request the HTTP request for admin authorization
     * @param redirectAttributes flash attributes to communicate success or error after redirect
     * @return redirect view string to {@code /admin/orders}
     */
    @PostMapping("/admin/orders/{orderId}/status")
    @Transactional
    public String updateStatus(
            @PathVariable long orderId,
            @RequestParam String status,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        adminController.requireAdminAccess(request);

        // Only allow valid transitions through the workflow.
        List<String> allowedStatuses = List.of(
                "PREPARING",
                "READY",
                "COMPLETED",
                "CANCELLED"
        );

        if (!allowedStatuses.contains(status)) {
            redirectAttributes.addFlashAttribute(
                    "error",
                    "Invalid order status."
            );

            return "redirect:/admin/orders";
        }

        // Update only if the order is paid and the transition satisfies the state machine rules
        int updated = jdbcTemplate.update(
                """
                UPDATE orders o
                JOIN payments p
                    ON p.id = o.payment_id
                SET o.status = ?
                WHERE o.id = ?
                  AND p.status = 'PAID'
                  AND (
                      (o.status = 'PLACED'
                          AND ? IN ('PREPARING', 'CANCELLED'))
                      OR
                      (o.status = 'PREPARING'
                          AND ? IN ('READY', 'CANCELLED'))
                      OR
                      (o.status = 'READY'
                          AND ? IN ('COMPLETED', 'CANCELLED'))
                  )
                """,
                status,
                orderId,
                status,
                status,
                status
        );

        if (updated == 1) {
            redirectAttributes.addFlashAttribute(
                    "success",
                    "Order #" + orderId
                            + " updated to " + status + "."
            );
        } else {
            redirectAttributes.addFlashAttribute(
                    "error",
                    "Order not found, not paid, or the status transition is not allowed."
            );
        }

        return "redirect:/admin/orders";
    }
}
