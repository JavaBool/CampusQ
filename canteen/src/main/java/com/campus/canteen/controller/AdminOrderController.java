
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

    @GetMapping("/admin/orders")
    public String paidOrders(
            HttpServletRequest request,
            Model model) {

        // Uses the existing ADMINSESSIONID authentication.
        adminController.requireAdminAccess(request);

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

        // Update only if the order is paid and the transition is valid.
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
