
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

    private ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                        "message", "Missing, invalid, expired, or revoked token."
                ));
    }

    public record ChangeNameRequest(String name) {}

    public record ChangeRollNumberRequest(String rollNumber) {}
}
