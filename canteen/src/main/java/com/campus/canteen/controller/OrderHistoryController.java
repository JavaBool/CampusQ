package com.campus.canteen.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Controller
public class OrderHistoryController {

    private final JdbcTemplate jdbcTemplate;

    public OrderHistoryController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record OrderLine(
            String productName,
            BigDecimal unitPrice,
            int quantity
    ) {
        public BigDecimal getSubtotal() {
            return unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }

    public record OrderSummary(
            long id,
            BigDecimal totalAmount,
            String orderStatus,
            String paymentStatus,
            String transactionId,
            Timestamp createdAt,
            List<OrderLine> items
    ) {}

    @GetMapping("/orders")
    public String orders(HttpSession session, Model model) {

        Object sessionUserId = session.getAttribute("userId");

        if (!(sessionUserId instanceof Number)) {
            return "redirect:/auth/login?returnUrl=/orders";
        }

        long userId = ((Number) sessionUserId).longValue();

        List<OrderSummary> orders = jdbcTemplate.query(
                """
                SELECT
                    o.id,
                    o.total_amount,
                    o.status AS order_status,
                    COALESCE(p.status, 'NOT INITIATED') AS payment_status,
                    o.transaction_id,
                    o.created_at
                FROM orders o
                LEFT JOIN payments p
                    ON p.id = o.payment_id
                WHERE o.user_id = ?
                ORDER BY o.created_at DESC, o.id DESC
                """,
                (rs, rowNum) -> new OrderSummary(
                        rs.getLong("id"),
                        rs.getBigDecimal("total_amount"),
                        rs.getString("order_status"),
                        rs.getString("payment_status"),
                        rs.getString("transaction_id"),
                        rs.getTimestamp("created_at"),
                        new ArrayList<>()
                ),
                userId
        );

        // Load each order's item details.
        List<OrderSummary> ordersWithItems = new ArrayList<>();

        for (OrderSummary order : orders) {
            List<OrderLine> items = jdbcTemplate.query(
                    """
                    SELECT product_name, unit_price, quantity
                    FROM order_items
                    WHERE order_id = ?
                    ORDER BY id
                    """,
                    (rs, rowNum) -> new OrderLine(
                            rs.getString("product_name"),
                            rs.getBigDecimal("unit_price"),
                            rs.getInt("quantity")
                    ),
                    order.id()
            );

            ordersWithItems.add(new OrderSummary(
                    order.id(),
                    order.totalAmount(),
                    order.orderStatus(),
                    order.paymentStatus(),
                    order.transactionId(),
                    order.createdAt(),
                    items
            ));
        }

        model.addAttribute("orders", ordersWithItems);
        model.addAttribute("fullName", session.getAttribute("fullName"));

        return "orders";
    }
}