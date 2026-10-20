package com.campus.canteen.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * Controller responsible for serving the public food and beverage menu page.
 *
 * <p>Queries the database directly using {@link JdbcTemplate} to retrieve all currently
 * active and available items, grouped and ordered by category and item name for display in
 * the {@code menu.html} Thymeleaf template.
 */
@Controller
public class MenuController {

    private final JdbcTemplate jdbcTemplate;

    public MenuController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Renders the canteen menu page with currently available items.
     *
     * <p>Only items flagged with {@code available = TRUE} are retrieved, preventing students
     * from viewing or ordering items that are currently out of stock.
     *
     * @param model the Spring MVC model for passing data to the view
     * @return the Thymeleaf view template name {@code "menu"}
     */
    @GetMapping("/menu")
    public String menu(Model model) {
        List<MenuItem> items = jdbcTemplate.query("""
                SELECT id, name, description, category, price,
                       image_link, available
                FROM items
                WHERE available = TRUE
                ORDER BY category, name
                """,
                (rs, rowNum) -> new MenuItem(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getString("category"),
                        rs.getBigDecimal("price"),
                        rs.getString("image_link"),
                        rs.getBoolean("available")
                ));

        model.addAttribute("items", items);
        return "menu";
    }

    /**
     * Immutable projection record representing an available menu item for view presentation.
     *
     * @param id the unique alphanumeric slug of the item
     * @param name human-readable item title
     * @param description detailed description or ingredients
     * @param category grouping category (e.g., Breakfast, Beverages)
     * @param price unit retail price
     * @param imageLink path or URL to the display image
     * @param available availability flag
     */
    public record MenuItem(
            String id,
            String name,
            String description,
            String category,
            java.math.BigDecimal price,
            String imageLink,
            boolean available
    ) {}
}