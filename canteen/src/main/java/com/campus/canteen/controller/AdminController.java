package com.campus.canteen.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

@Controller
public class AdminController {

    private final JdbcTemplate jdbcTemplate;
    private final String adminUsername;
    private final String adminPassword;

    public AdminController(
            JdbcTemplate jdbcTemplate,
            @Value("${app.admin.username}") String adminUsername,
            @Value("${app.admin.password}") String adminPassword) {
        this.jdbcTemplate = jdbcTemplate;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    // Dashboard
    @GetMapping("/admin")
    public String adminPage() {
        return "admin";
    }

    // Login
    @PostMapping("/admin/api/login")
    @ResponseBody
    public ResponseEntity<?> login(
            @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        if (request.username() == null || request.password() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Username and password are required."));
        }

        boolean validUsername = constantTimeEquals(
                request.username(), adminUsername);
        boolean validPassword = constantTimeEquals(
                request.password(), adminPassword);

        if (!validUsername || !validPassword) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid username or password."));
        }

        HttpSession session = httpRequest.getSession(true);

        if (httpRequest.getSession(false) != null) {
            httpRequest.changeSessionId();
        }
        session.setAttribute("campusBiteAdminAuthenticated", true);

        return ResponseEntity.ok(Map.of("message", "Login successful."));
    }

    // Logout without logging the user out of their normal website account
    @PostMapping("/admin/api/logout")
    @ResponseBody
    public ResponseEntity<?> logout(HttpSession session) {
        session.removeAttribute("campusBiteAdminAuthenticated");

        return ResponseEntity.ok(Map.of("message", "Logged out."));
    }

    // Read all items
    @GetMapping("/admin/api/items")
    @ResponseBody
    public List<MenuItem> getItems(HttpSession session) {
        requireAdmin(session);

        return jdbcTemplate.query("""
                SELECT id, name, description, category, price,
                       image_link, available
                FROM items
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
    }

    // Add item
    @PostMapping("/admin/api/items")
    @ResponseBody
    public ResponseEntity<?> createItem(
            @RequestBody ItemRequest request,
            HttpSession session) {

        requireAdmin(session);
        validateItem(request);

        try {
            jdbcTemplate.update("""
                    INSERT INTO items
                        (id, name, description, category, price,
                         image_link, available)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    request.id().trim(),
                    request.name().trim(),
                    blankToNull(request.description()),
                    request.category().trim(),
                    request.price(),
                    blankToNull(request.imageLink()),
                    request.available()
            );

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("message", "Item created successfully."));
        } catch (DuplicateKeyException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "An item with that ID already exists."));
        }
    }

    // Update every editable field, including the item ID
    @PutMapping("/admin/api/items/{oldId}")
    @ResponseBody
    public ResponseEntity<?> updateItem(
            @PathVariable String oldId,
            @RequestBody ItemRequest request,
            HttpSession session) {

        requireAdmin(session);
        validateItem(request);

        try {
            int updated = jdbcTemplate.update("""
                    UPDATE items
                    SET id = ?, name = ?, description = ?, category = ?,
                        price = ?, image_link = ?, available = ?
                    WHERE id = ?
                    """,
                    request.id().trim(),
                    request.name().trim(),
                    blankToNull(request.description()),
                    request.category().trim(),
                    request.price(),
                    blankToNull(request.imageLink()),
                    request.available(),
                    oldId
            );

            if (updated == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Item not found."));
            }

            return ResponseEntity.ok(
                    Map.of("message", "Item updated successfully."));
        } catch (DuplicateKeyException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "That item ID is already in use."));
        }
    }

    // Delete item
    @DeleteMapping("/admin/api/items/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteItem(
            @PathVariable String id,
            HttpSession session) {

        requireAdmin(session);

        int deleted = jdbcTemplate.update(
                "DELETE FROM items WHERE id = ?", id);

        if (deleted == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Item not found."));
        }

        return ResponseEntity.ok(
                Map.of("message", "Item deleted successfully."));
    }

    private void requireAdmin(HttpSession session) {
        if (session.getAttribute("campusBiteAdminAuthenticated") != Boolean.TRUE) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Admin login required.");
        }
    }

    private void validateItem(ItemRequest item) {
        if (item == null
                || item.id() == null || item.id().isBlank()
                || item.name() == null || item.name().isBlank()
                || item.category() == null || item.category().isBlank()
                || item.price() == null || item.price().signum() < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "ID, name, category and a non-negative price are required.");
        }

        if (item.id().trim().length() > 60
                || item.name().trim().length() > 120
                || item.category().trim().length() > 50
                || (item.description() != null && item.description().length() > 500)
                || (item.imageLink() != null && item.imageLink().length() > 2048)
                || item.price().scale() > 2
                || item.price().precision() > 10) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "One or more fields are too long or invalid.");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    public record LoginRequest(String username, String password) {}

    public record ItemRequest(
            String id,
            String name,
            String description,
            String category,
            BigDecimal price,
            String imageLink,
            boolean available) {}

    public record MenuItem(
            String id,
            String name,
            String description,
            String category,
            BigDecimal price,
            String imageLink,
            boolean available) {}
}