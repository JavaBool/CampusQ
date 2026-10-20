
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

/**
 * Controller managing cart checkout validation and order placement for website customers.
 *
 * <p>Key Invariants &amp; Security Controls:
 * <ul>
 *   <li><b>Session &amp; Account Authentication:</b> Verifies that the user has an active session and confirms
 *       their account still exists in MySQL before proceeding with order placement.</li>
 *   <li><b>Authoritative Server-Side Pricing:</b> Client-submitted prices are never accepted or trusted.
 *       Unit prices are fetched directly from the database table {@code items} where {@code available = TRUE}.
 *       If an item has been disabled or removed by an administrator, checkout is rejected immediately.</li>
 *   <li><b>Cart Consolidation:</b> Merges duplicate item IDs submitted by the client into single line entries,
 *       enforcing a maximum cap of 100 units per item.</li>
 *   <li><b>Atomic Transaction Boundaries:</b> The order master record and all constituent {@code order_items}
 *       records are persisted within a single {@link Transactional} boundary, ensuring zero orphaned records
 *       if an insert fails.</li>
 * </ul>
 */
@Controller
public class OrderController {

    private final JdbcTemplate jdbcTemplate;

    public OrderController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Internal record for binding queried active menu items. */
    private record MenuItem(
            String id,
            String name,
            BigDecimal price
    ) {}

    /** Internal record representing a validated, priced line item with quantity. */
    private record PricedItem(
            String id,
            String name,
            BigDecimal price,
            int quantity
    ) {}

    /**
     * Processes customer checkout submissions, validates availability, computes authoritative totals,
     * persists the order and its line items, and displays the order bill.
     *
     * @param form form-backing DTO containing submitted cart items
     * @param session the user's current HTTP session
     * @param model Spring MVC model for rendering view attributes
     * @return the {@code "checkout-bill"} view on success, {@code "checkout-error"} on validation failure,
     *         or redirect to login if unauthenticated
     */
    @PostMapping("/order/checkout")
    @Transactional
    public String checkout(
            @ModelAttribute CheckoutForm form,
            HttpSession session,
            Model model) {

        Object sessionUserId = session.getAttribute("userId");

        // Enforce user authentication; redirect to login with returnUrl to preserve cart context
        if (!(sessionUserId instanceof Number)) {
            return "redirect:/auth/login?returnUrl=/cart";
        }

        long userId = ((Number) sessionUserId).longValue();

        // Ensure the session user actually exists in the database
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

        // Merge duplicate item IDs and validate quantity boundaries before querying or inserting.
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

        // Validate every item against current active inventory and obtain authoritative prices
        // directly from the database to prevent client tampering.
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

        // Insert master order with initial status PLACED
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

        // Insert historical line item snapshots preserving product name and purchase price
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

        // Prepare items with precomputed subtotals for Thymeleaf view rendering
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

        // Instruct the client-side JavaScript to clear local cart state after successful order creation
        model.addAttribute("clearCart", true);
        return "checkout-bill";
    }

    /**
     * Helper method to populate an error message and route to the checkout error view.
     *
     * @param model the Spring MVC model
     * @param message the explanatory error message
     * @return the {@code "checkout-error"} view name
     */
    private String showError(Model model, String message) {
        model.addAttribute("error", message);
        return "checkout-error";
    }
}
