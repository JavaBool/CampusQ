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

/**
 * Controller serving the customer's personal order history view.
 *
 * <p>Authentication &amp; Presentation:
 * <ul>
 *   <li>Requires an authenticated student session; unauthenticated requests are redirected
 *       to {@code /auth/login?returnUrl=/orders} to preserve user intent.</li>
 *   <li>Uses a SQL {@code LEFT JOIN} against the {@code payments} table so orders where payment
 *       was never initiated still display with payment status {@code 'NOT INITIATED'}.</li>
 *   <li>Populates child {@link OrderLine} snapshots containing the product name, unit price,
 *       and quantity as captured at the time of purchase.</li>
 * </ul>
 */
@Controller
public class OrderHistoryController {

    private final JdbcTemplate jdbcTemplate;

    public OrderHistoryController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Immutable projection record representing an individual purchased line item.
     *
     * @param productName the snapshot name of the product at purchase
     * @param unitPrice the unit price billed at checkout
     * @param quantity the count of items ordered
     */
    public record OrderLine(
            String productName,
            BigDecimal unitPrice,
            int quantity
    ) {
        /** Calculates the line total (unit price × quantity). */
        public BigDecimal getSubtotal() {
            return unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }

    /**
     * Immutable projection record representing an entire order including status, payment, and items.
     *
     * @param id order primary key
     * @param totalAmount aggregate bill amount
     * @param orderStatus fulfillment status (e.g., PLACED, PREPARING, READY, COMPLETED, CANCELLED)
     * @param paymentStatus payment state (e.g., PENDING, PAID, NOT INITIATED)
     * @param transactionId cryptographic payment transaction ID, or null
     * @param createdAt timestamp when the order was placed
     * @param items list of line item records
     */
    public record OrderSummary(
            long id,
            BigDecimal totalAmount,
            String orderStatus,
            String paymentStatus,
            String transactionId,
            Timestamp createdAt,
            List<OrderLine> items
    ) {}

    /**
     * Renders the order history page for the currently logged-in student.
     *
     * @param session the user's HTTP session
     * @param model the Spring MVC model for view rendering
     * @return the {@code "orders"} view template, or redirect to login if unauthenticated
     */
    @GetMapping("/orders")
    public String orders(HttpSession session, Model model) {

        Object sessionUserId = session.getAttribute("userId");

        if (!(sessionUserId instanceof Number)) {
            return "redirect:/auth/login?returnUrl=/orders";
        }

        long userId = ((Number) sessionUserId).longValue();

        // Query orders with LEFT JOIN on payments to handle unpaid or uninitiated checkouts
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

        // Load historical item snapshot lines for each order
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