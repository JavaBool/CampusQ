
package com.campus.canteen.controller;

import com.campus.canteen.model.CheckoutForm;
import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.math.BigDecimal;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class OrderController {

    private final JdbcTemplate jdbcTemplate;

    public OrderController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private record MenuItem(
            String id,
            String name,
            BigDecimal price
    ) {}

    private record PricedItem(
            String id,
            String name,
            BigDecimal price,
            int quantity
    ) {}

    @PostMapping("/order/checkout")
    @Transactional
    public String checkout(
            @ModelAttribute CheckoutForm form,
            HttpSession session,
            Model model) {

        Object sessionUserId = session.getAttribute("userId");

        if (!(sessionUserId instanceof Number)) {
            return "redirect:/auth/login?returnUrl=/cart";
        }

        long userId = ((Number) sessionUserId).longValue();

        Integer userExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ?",
                Integer.class,
                userId
        );

        if (userExists == null || userExists == 0) {
            session.invalidate();
            return "redirect:/auth/login?returnUrl=/cart";
        }

        List<CheckoutForm.CartLine> submitted = form.getItems();

        if (submitted == null || submitted.isEmpty()) {
            return showError(model, "Your cart is empty.");
        }

        // Merge duplicate item IDs before validating and inserting.
        Map<String, Integer> quantities = new LinkedHashMap<>();

        for (CheckoutForm.CartLine line : submitted) {
            if (line == null
                    || line.getItemId() == null
                    || line.getItemId().isBlank()
                    || line.getQuantity() < 1
                    || line.getQuantity() > 100) {
                return showError(model, "Invalid item or quantity.");
            }

            String itemId = line.getItemId().trim();

            int combined = quantities.getOrDefault(itemId, 0)
                    + line.getQuantity();

            if (combined > 100) {
                return showError(
                        model,
                        "Maximum quantity per item is 100."
                );
            }

            quantities.put(itemId, combined);
        }

        // Validate every item and obtain authoritative prices
        // before inserting the order.
        List<PricedItem> pricedItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Map.Entry<String, Integer> entry : quantities.entrySet()) {
            List<MenuItem> matches = jdbcTemplate.query(
                    """
                    SELECT id, name, price
                    FROM items
                    WHERE id = ? AND available = TRUE
                    """,
                    (rs, rowNum) -> new MenuItem(
                            rs.getString("id"),
                            rs.getString("name"),
                            rs.getBigDecimal("price")
                    ),
                    entry.getKey()
            );

            if (matches.isEmpty()) {
                return showError(
                        model,
                        "An item is unavailable. Please refresh your menu."
                );
            }

            MenuItem item = matches.get(0);
            int quantity = entry.getValue();

            pricedItems.add(new PricedItem(
                    item.id(),
                    item.name(),
                    item.price(),
                    quantity
            ));

            total = total.add(
                    item.price().multiply(BigDecimal.valueOf(quantity))
            );
        }

        if (total.signum() <= 0) {
            return showError(model, "The order total must be positive.");
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();

        final BigDecimal orderTotal = total;

        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                    """
                    INSERT INTO orders
                        (user_id, payment_id, transaction_id,
                         total_amount, status)
                    VALUES (?, NULL, NULL, ?, 'PLACED')
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );

            statement.setLong(1, userId);
            statement.setBigDecimal(2, orderTotal);

            return statement;
        }, keyHolder);

        Number generatedKey = keyHolder.getKey();

        if (generatedKey == null) {
            throw new IllegalStateException("Failed to generate order ID.");
        }

        long orderId = generatedKey.longValue();

        for (PricedItem item : pricedItems) {
            jdbcTemplate.update(
                    """
                    INSERT INTO order_items
                        (order_id, product_id, product_name,
                         unit_price, quantity)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    orderId,
                    item.id(),
                    item.name(),
                    item.price(),
                    item.quantity()
            );
        }

        // Prepare items with precomputed subtotals for Thymeleaf.
        List<Map<String, Object>> billItems = pricedItems.stream()
                .map(item -> {
                    Map<String, Object> row = new LinkedHashMap<>();

                    row.put("id", item.id());
                    row.put("name", item.name());
                    row.put("price", item.price());
                    row.put("quantity", item.quantity());
                    row.put(
                            "subtotal",
                            item.price().multiply(
                                    BigDecimal.valueOf(item.quantity())
                            )
                    );

                    return row;
                })
                .toList();

        model.addAttribute("orderId", orderId);
        model.addAttribute("items", billItems);
        model.addAttribute("total", total);

        return "checkout-bill";
    }

    private String showError(Model model, String message) {
        model.addAttribute("error", message);
        return "checkout-error";
    }
}
