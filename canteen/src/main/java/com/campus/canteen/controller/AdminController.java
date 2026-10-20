package com.campus.canteen.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controller managing administrative authentication, dashboard routing, and menu inventory CRUD operations.
 *
 * <p>Security &amp; Architectural Design:
 * <ul>
 *   <li><b>Privilege Isolation:</b> Completely bypasses standard servlet {@link jakarta.servlet.http.HttpSession}
 *       in favor of an internal, in-memory {@link ConcurrentHashMap} of {@link AdminSession} instances. This isolates
 *       student and administrative privilege contexts, preventing session pollution, elevation of privilege,
 *       or session fixation across roles.</li>
 *   <li><b>Timing-Attack Resistance:</b> Administrative username and password checks use {@link MessageDigest#isEqual(byte[], byte[])}
 *       to ensure constant-time byte comparisons, eliminating side-channel timing analysis.</li>
 *   <li><b>Hardened Session Cookies:</b> Issues {@code ADMINSESSIONID} with {@code HttpOnly=true},
 *       {@code SameSite=Strict}, scoped strictly to {@code path="/admin"} with an 8-hour lifetime.</li>
 *   <li><b>Opportunistic Session Eviction:</b> Inactive/expired sessions are purged from memory during
 *       authentication attempts.</li>
 *   <li><b>Primary Key Mutation:</b> The {@code updateItem} operation supports updating an item's primary key
 *       {@code id} slug itself, handling unique constraint collisions by returning HTTP 409 Conflict.</li>
 * </ul>
 */
@Controller
public class AdminController {

    private static final String ADMIN_COOKIE = "ADMINSESSIONID";
    private static final long SESSION_LIFETIME_MS =
            8L * 60 * 60 * 1000; // 8 hours

    private final JdbcTemplate jdbcTemplate;
    private final String adminUsername;
    private final String adminPassword;
    private final boolean secureCookie;

    private final SecureRandom secureRandom = new SecureRandom();

    /*
     * Completely separate from the normal user's HttpSession.
     * This map contains only administrator sessions.
     */
    private final Map<String, AdminSession> adminSessions =
            new ConcurrentHashMap<>();

    public AdminController(
            JdbcTemplate jdbcTemplate,
            @Value("${app.admin.username}") String adminUsername,
            @Value("${app.admin.password}") String adminPassword,
            @Value("${app.admin.cookie-secure:false}") boolean secureCookie) {

        this.jdbcTemplate = jdbcTemplate;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.secureCookie = secureCookie;
    }

    // ---------------------------------------------------------
    // Dashboard
    // ---------------------------------------------------------

    /**
     * Renders the administrative management dashboard page.
     *
     * @return the {@code "admin"} Thymeleaf template
     */
    @GetMapping("/admin")
    public String adminPage() {
        return "admin";
    }

    // ---------------------------------------------------------
    // Admin login
    // ---------------------------------------------------------

    /**
     * Authenticates administrator credentials and establishes an isolated administrative session cookie.
     *
     * <p>Mitigates timing attacks by using constant-time byte comparisons on credentials,
     * and sets an {@code HttpOnly}, {@code SameSite=Strict} cookie scoped exclusively to {@code /admin}.
     *
     * @param request JSON request containing admin username and password
     * @return 200 OK with session cookie, 400 Bad Request on missing fields, or 401 Unauthorized
     */
    @PostMapping("/admin/api/login")
    @ResponseBody
    public ResponseEntity<?> login(
            @RequestBody LoginRequest request) {

        if (request == null
                || request.username() == null
                || request.password() == null) {

            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            "Username and password are required."
                    ));
        }

        // Constant-time comparisons mitigate side-channel timing analysis
        boolean validUsername = constantTimeEquals(
                request.username(), adminUsername);

        boolean validPassword = constantTimeEquals(
                request.password(), adminPassword);

        if (!validUsername || !validPassword) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "message",
                            "Invalid username or password."
                    ));
        }

        // Generate an independent, unpredictable admin session ID.
        String sessionId = generateSessionId();

        // Opportunistically remove expired sessions.
        removeExpiredSessions();

        adminSessions.put(
                sessionId,
                new AdminSession(
                        adminUsername,
                        System.currentTimeMillis() + SESSION_LIFETIME_MS
                )
        );

        ResponseCookie cookie = ResponseCookie
                .from(ADMIN_COOKIE, sessionId)
                .httpOnly(true)
                .secure(secureCookie)
                .path("/admin")
                .sameSite("Strict")
                .maxAge(SESSION_LIFETIME_MS / 1000)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(Map.of(
                        "message", "Login successful."
                ));
    }

    // ---------------------------------------------------------
    // Admin logout
    // ---------------------------------------------------------

    /**
     * Terminates the current administrator session, removing it from the session registry
     * and clearing the {@code ADMINSESSIONID} cookie.
     *
     * @param request the current HTTP request containing the session cookie
     * @return 200 OK with an expired cookie header
     */
    @PostMapping("/admin/api/logout")
    @ResponseBody
    public ResponseEntity<?> logout(
            HttpServletRequest request) {

        String sessionId = getAdminSessionId(request);

        if (sessionId != null) {
            adminSessions.remove(sessionId);
        }

        ResponseCookie expiredCookie = ResponseCookie
                .from(ADMIN_COOKIE, "")
                .httpOnly(true)
                .secure(secureCookie)
                .path("/admin")
                .sameSite("Strict")
                .maxAge(0)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredCookie.toString())
                .body(Map.of("message", "Logged out."));
    }

    // ---------------------------------------------------------
    // Read menu items
    // ---------------------------------------------------------

    /**
     * Returns the full inventory of menu items (both available and unavailable) for administrative management.
     *
     * @param request the HTTP request validated for admin authentication
     * @return list of all menu items in the database
     * @throws ResponseStatusException 401 Unauthorized if admin cookie is missing or invalid
     */
    @GetMapping("/admin/api/items")
    @ResponseBody
    public List<MenuItem> getItems(
            HttpServletRequest request) {

        requireAdmin(request);

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

    // ---------------------------------------------------------
    // Add menu item
    // ---------------------------------------------------------

    /**
     * Creates a new menu item in the catalog.
     *
     * @param request validated item payload
     * @param httpRequest current HTTP request for admin authorization
     * @return 201 Created on success, 400 Bad Request on validation error, or 409 Conflict if ID exists
     * @throws ResponseStatusException 401 Unauthorized if unauthenticated
     */
    @PostMapping("/admin/api/items")
    @ResponseBody
    public ResponseEntity<?> createItem(
            @RequestBody ItemRequest request,
            HttpServletRequest httpRequest) {

        requireAdmin(httpRequest);
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
                    .body(Map.of(
                            "message", "Item created successfully."
                    ));

        } catch (DuplicateKeyException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "message", "An item with that ID already exists."
                    ));
        }
    }

    // ---------------------------------------------------------
    // Update menu item, including its ID
    // ---------------------------------------------------------

    /**
     * Updates an existing menu item, including its primary key identifier slug.
     *
     * @param oldId the current item identifier
     * @param request the updated item payload
     * @param httpRequest current HTTP request for admin authorization
     * @return 200 OK on success, 404 Not Found if item does not exist, or 409 Conflict if new ID is taken
     * @throws ResponseStatusException 401 Unauthorized if unauthenticated
     */
    @PutMapping("/admin/api/items/{oldId}")
    @ResponseBody
    public ResponseEntity<?> updateItem(
            @PathVariable String oldId,
            @RequestBody ItemRequest request,
            HttpServletRequest httpRequest) {

        requireAdmin(httpRequest);
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
                    .body(Map.of(
                            "message", "That item ID is already in use."
                    ));
        }
    }

    // ---------------------------------------------------------
    // Delete menu item
    // ---------------------------------------------------------

    /**
     * Deletes a menu item from the catalog by ID.
     *
     * @param id the item ID to delete
     * @param request current HTTP request for admin authorization
     * @return 200 OK on deletion, or 404 Not Found
     * @throws ResponseStatusException 401 Unauthorized if unauthenticated
     */
    @DeleteMapping("/admin/api/items/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteItem(
            @PathVariable String id,
            HttpServletRequest request) {

        requireAdmin(request);

        int deleted = jdbcTemplate.update(
                "DELETE FROM items WHERE id = ?", id);

        if (deleted == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Item not found."));
        }

        return ResponseEntity.ok(
                Map.of("message", "Item deleted successfully."));
    }

    // ---------------------------------------------------------
    // Independent admin authentication helpers
    // ---------------------------------------------------------

    /**
     * Validates that the request carries a valid, non-expired administrator session cookie.
     *
     * @throws ResponseStatusException 401 Unauthorized if authentication fails
     */
    private void requireAdmin(HttpServletRequest request) {

        String sessionId = getAdminSessionId(request);

        if (sessionId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Admin login required."
            );
        }

        AdminSession session = adminSessions.get(sessionId);

        if (session == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Admin session is invalid. Please sign in again."
            );
        }

        if (System.currentTimeMillis() >= session.expiresAt()) {
            adminSessions.remove(sessionId, session);

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Admin session has expired. Please sign in again."
            );
        }
    }

    /**
     * Extracts and validates the length of the {@code ADMINSESSIONID} cookie from request headers.
     */
    private String getAdminSessionId(HttpServletRequest request) {

        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (ADMIN_COOKIE.equals(cookie.getName())) {
                String value = cookie.getValue();

                if (value != null
                        && !value.isBlank()
                        && value.length() <= 100) {
                    return value;
                }

                return null;
            }
        }

        return null;
    }

    /**
     * Generates a 256-bit cryptographically secure session ID for the admin registry.
     */
    private String generateSessionId() {

        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    /**
     * Opportunistically removes expired administrator sessions from the concurrent session map.
     */
    private void removeExpiredSessions() {

        long now = System.currentTimeMillis();

        adminSessions.entrySet().removeIf(
                entry -> now >= entry.getValue().expiresAt()
        );
    }

    // ---------------------------------------------------------
    // Menu item validation
    // ---------------------------------------------------------

    /**
     * Validates menu item field constraints including lengths, non-negativity, and decimal precision.
     *
     * @param item the item request DTO
     * @throws ResponseStatusException 400 Bad Request if validation constraints fail
     */
    private void validateItem(ItemRequest item) {

        if (item == null
                || item.id() == null || item.id().isBlank()
                || item.name() == null || item.name().isBlank()
                || item.category() == null || item.category().isBlank()
                || item.price() == null
                || item.price().signum() < 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "ID, name, category and a non-negative price are required."
            );
        }

        if (item.id().trim().length() > 60
                || item.name().trim().length() > 120
                || item.category().trim().length() > 50
                || (item.description() != null
                && item.description().length() > 500)
                || (item.imageLink() != null
                && item.imageLink().length() > 2048)
                || item.price().scale() > 2
                || item.price().precision() > 10) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "One or more fields are too long or invalid."
            );
        }
    }

    /** Converts blank strings to SQL null to prevent empty string storage in nullable columns. */
    private String blankToNull(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }

    /** Constant-time byte-level string comparison to defend against timing attacks. */
    private boolean constantTimeEquals(String a, String b) {

        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Public gateway allowing sibling controllers (e.g. {@link AdminOrderController}) to verify
     * the administrative session cookie.
     *
     * @param request the HTTP request
     * @throws ResponseStatusException 401 Unauthorized if not authenticated as admin
     */
    public void requireAdminAccess(HttpServletRequest request) {
        requireAdmin(request);
    }

    // ---------------------------------------------------------
    // DTOs and admin session record
    // ---------------------------------------------------------

    /** Internal record storing an authenticated administrator username and expiration epoch. */
    private record AdminSession(
            String username,
            long expiresAt
    ) {}

    /** DTO for administrator login requests. */
    public record LoginRequest(
            String username,
            String password
    ) {}

    /** DTO for creating or updating a menu catalog item. */
    public record ItemRequest(
            String id,
            String name,
            String description,
            String category,
            BigDecimal price,
            String imageLink,
            boolean available
    ) {}

    /** Projection record representing a catalog item in API responses. */
    public record MenuItem(
            String id,
            String name,
            String description,
            String category,
            BigDecimal price,
            String imageLink,
            boolean available
    ) {}
}