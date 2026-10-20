package com.campus.canteen.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@Service
public class MenuCatalogService {

    private final Map<String, MenuItem> items = Map.of(
            "masala-dosa",
            new MenuItem("masala-dosa", "Masala Dosa", new BigDecimal("55.00")),

            "veg-fried-rice",
            new MenuItem("veg-fried-rice", "Veg Fried Rice", new BigDecimal("80.00")),

            "paneer-wrap",
            new MenuItem("paneer-wrap", "Paneer Wrap", new BigDecimal("70.00")),

            "cheese-sandwich",
            new MenuItem("cheese-sandwich", "Grilled Cheese Sandwich",
                    new BigDecimal("60.00")),

            "filter-coffee",
            new MenuItem("filter-coffee", "Filter Coffee", new BigDecimal("20.00")),

            "lime-soda",
            new MenuItem("lime-soda", "Fresh Lime Soda", new BigDecimal("30.00")),

            "brownie",
            new MenuItem("brownie", "Chocolate Brownie", new BigDecimal("45.00")),

            "idli-sambar",
            new MenuItem("idli-sambar", "Idli & Sambar", new BigDecimal("35.00"))
    );

    public Optional<MenuItem> findById(String id) {
        return Optional.ofNullable(items.get(id));
    }

    public record MenuItem(
            String id,
            String name,
            BigDecimal price
    ) {}
}