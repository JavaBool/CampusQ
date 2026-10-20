
package com.campus.canteen.controller;

import com.campus.canteen.service.ApiTokenService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller providing user profile management for mobile and stateless API v2 clients.
 *
 * <p>Authentication is enforced on every endpoint via {@code Authorization: Bearer <token>} headers
 * verified by {@link ApiTokenService#findUserIdByToken(String)}.
 */
@RestController
@RequestMapping("/api/v2/profile")
public class V2ProfileController {

    private final JdbcTemplate jdbcTemplate;
    private final ApiTokenService apiTokenService;

    public V2ProfileController(
            JdbcTemplate jdbcTemplate,
            ApiTokenService apiTokenService) {
        this.jdbcTemplate = jdbcTemplate;
        this.apiTokenService = apiTokenService;
    }

    /**
     * Retrieves account details for the authenticated Bearer token owner.
     *
     * @param authorization the {@code Authorization: Bearer <token>} header
     * @return 200 OK with account details JSON, or 401 Unauthorized
     */
    // POST /api/v2/profile/details
    @PostMapping("/details")
    public ResponseEntity<?> getDetails(
            @RequestHeader(value = "Authorization", required = false)
            String authorization) {

        Optional<Long> userId = getAuthenticatedUserId(authorization);

        if (userId.isEmpty()) {
            return unauthorized();
        }

        return jdbcTemplate.query("""
                SELECT id, full_name, email, roll_number, created_at
                FROM users
                WHERE id = ?
                """,
                rs -> {
                    if (!rs.next()) {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(Map.of("message", "User account not found."));
                    }

                    Timestamp createdAt = rs.getTimestamp("created_at");

                    return ResponseEntity.ok(Map.of(
                            "id", rs.getLong("id"),
                            "fullName", rs.getString("full_name"),
                            "email", rs.getString("email"),
                            "rollNumber", rs.getString("roll_number"),
                            "createdAt", createdAt == null
                                     ? ""
                                    : createdAt.toInstant().toString()
                    ));
                },
                userId.get()
        );
    }

    /**
     * Updates the full name of the authenticated user.
     *
     * @param authorization the {@code Authorization: Bearer <token>} header
     * @param request JSON payload containing the updated name (1 to 100 characters)
     * @return 200 OK with success confirmation, 400 Bad Request, or 401 Unauthorized
     */
    // POST /api/v2/profile/change/name
    @PostMapping("/change/name")
    public ResponseEntity<?> changeName(
            @RequestHeader(value = "Authorization", required = false)
            String authorization,
            @RequestBody ChangeNameRequest request) {

        Optional<Long> userId = getAuthenticatedUserId(authorization);

        if (userId.isEmpty()) {
            return unauthorized();
        }

        if (request == null || request.name() == null
                || request.name().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Name is required."));
        }

        String name = request.name().trim();

        if (name.length() > 100) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Name must not exceed 100 characters."));
        }

        int updated = jdbcTemplate.update("""
                UPDATE users
                SET full_name = ?
                WHERE id = ?
                """,
                name,
                userId.get()
        );

        if (updated == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "User account not found."));
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Name updated successfully.",
                "fullName", name
        ));
    }

    /**
     * Updates the academic roll number of the authenticated user.
     *
     * @param authorization the {@code Authorization: Bearer <token>} header
     * @param request JSON payload containing the new roll number (1 to 50 characters)
     * @return 200 OK with success confirmation, 400 Bad Request, 409 Conflict if claimed, or 401 Unauthorized
     */
    // POST /api/v2/profile/change/roll_no
    @PostMapping("/change/roll_no")
    public ResponseEntity<?> changeRollNumber(
            @RequestHeader(value = "Authorization", required = false)
            String authorization,
            @RequestBody ChangeRollNumberRequest request) {

        Optional<Long> userId = getAuthenticatedUserId(authorization);

        if (userId.isEmpty()) {
            return unauthorized();
        }

        if (request == null || request.rollNumber() == null
                || request.rollNumber().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Roll number is required."));
        }

        String rollNumber = request.rollNumber().trim();

        if (rollNumber.length() > 50) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Roll number must not exceed 50 characters."));
        }

        try {
            int updated = jdbcTemplate.update("""
                    UPDATE users
                    SET roll_number = ?
                    WHERE id = ?
                    """,
                    rollNumber,
                    userId.get()
            );

            if (updated == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "User account not found."));
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Roll number updated successfully.",
                    "rollNumber", rollNumber
            ));

        } catch (DuplicateKeyException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "message", "That roll number is already registered."
                    ));
        }
    }

    /** Helper extracting and resolving user ID from Bearer token header. */
    private Optional<Long> getAuthenticatedUserId(String authorization) {
        if (authorization == null
                || !authorization.startsWith("Bearer ")) {
            return Optional.empty();
        }

        String token = authorization.substring(7).trim();

        if (token.isEmpty()) {
            return Optional.empty();
        }

        return apiTokenService.findUserIdByToken(token);
    }

    /** Helper building standard 401 Unauthorized JSON error response. */
    private ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                        "message", "Missing, invalid, expired, or revoked token."
                ));
    }

    /** DTO for API v2 full name change requests. */
    public record ChangeNameRequest(String name) {}

    /** DTO for API v2 roll number change requests. */
    public record ChangeRollNumberRequest(String rollNumber) {}
}
