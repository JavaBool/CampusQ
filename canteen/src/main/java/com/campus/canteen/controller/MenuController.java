package com.campus.canteen.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class MenuController {

    private final JdbcTemplate jdbcTemplate;

    public MenuController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

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