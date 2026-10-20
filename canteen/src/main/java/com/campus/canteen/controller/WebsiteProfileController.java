
package com.campus.canteen.controller;
import com.campus.canteen.service.ApiTokenService;
import com.campus.canteen.service.ApiTokenService;
import com.campus.canteen.service.AuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controller handling in-place profile updates (name, roll number, password) from the website profile page.
 *
 * <p>Security &amp; Architectural Features:
 * <ul>
 *   <li><b>Token-Backed Session Verification:</b> Validates authentication via {@link #getAuthenticatedUserId(HttpSession)},
 *       which resolves the session's {@code apiToken} attribute against MySQL. This prevents unauthorized actions
 *       if the token was revoked externally.</li>
 *   <li><b>Seamless Token Renewal on Password Change:</b> {@link AuthService#changePassword(long, String, String)}
 *       revokes all existing API tokens for the user upon a successful password change. This controller generates
 *       a brand-new replacement token and stores it into the current {@link HttpSession}, keeping the active browser
 *       logged in while successfully terminating any external sessions.</li>
 *   <li><b>Constraint Translation:</b> Catches database {@link DataIntegrityViolationException} on duplicate
 *       student roll numbers and converts it to a clean HTTP 409 Conflict status.</li>
 * </ul>
 */
@RestController
public class WebsiteProfileController {

    private final AuthService authService;
    private final ApiTokenService apiTokenService;

    public WebsiteProfileController(
            AuthService authService,
            ApiTokenService apiTokenService) {
        this.authService = authService;
        this.apiTokenService = apiTokenService;
    }

    /**
     * Updates the full name for the logged-in student.
     *
     * @param request JSON body containing the updated full name
     * @param session the user's HTTP session
     * @return 200 OK with the updated name, 400 Bad Request on invalid input, or 401 Unauthorized
     */
    @PostMapping("/profile/change/name")
    public ResponseEntity<Map<String, String>> changeName(
            @RequestBody ChangeNameRequest request,
            HttpSession session) {

        Long userId = getAuthenticatedUserId(session);

        if (userId == null) {
            return error(HttpStatus.UNAUTHORIZED,
                    "Your session has expired. Please sign in again.");
        }

        if (request.fullName() == null
                || request.fullName().isBlank()) {
            return error(HttpStatus.BAD_REQUEST,
                    "Full name cannot be empty.");
        }

        String fullName = request.fullName().trim();

        if (fullName.length() > 100) {
            return error(HttpStatus.BAD_REQUEST,
                    "Full name cannot exceed 100 characters.");
        }

        try {
            authService.updateFullName(userId, fullName);
        } catch (IllegalArgumentException exception) {
            return error(HttpStatus.BAD_REQUEST, exception.getMessage());
        }

        // Keep the website session's displayed name in sync.
        session.setAttribute("fullName", fullName);

        return ResponseEntity.ok(Map.of(
                "message", "Name updated successfully.",
                "fullName", fullName
        ));
    }

    /**
     * Updates the unique student roll number for the logged-in user.
     *
     * @param request JSON body containing the new roll number
     * @param session the user's HTTP session
     * @return 200 OK on success, 409 Conflict if roll number is already claimed, or 401 Unauthorized
     */
    @PostMapping("/profile/change/roll-no")
    public ResponseEntity<Map<String, String>> changeRollNumber(
            @RequestBody ChangeRollNumberRequest request,
            HttpSession session) {

        Long userId = getAuthenticatedUserId(session);

        if (userId == null) {
            return error(HttpStatus.UNAUTHORIZED,
                    "Your session has expired. Please sign in again.");
        }

        if (request.rollNumber() == null
                || request.rollNumber().isBlank()) {
            return error(HttpStatus.BAD_REQUEST,
                    "Roll number cannot be empty.");
        }

        String rollNumber = request.rollNumber().trim();

        if (rollNumber.length() > 50) {
            return error(HttpStatus.BAD_REQUEST,
                    "Roll number cannot exceed 50 characters.");
        }

        try {
            authService.updateRollNumber(userId, rollNumber);
        } catch (DataIntegrityViolationException exception) {
            return error(HttpStatus.CONFLICT,
                    "That roll number is already registered to another account.");
        } catch (IllegalArgumentException exception) {
            return error(HttpStatus.BAD_REQUEST, exception.getMessage());
        }

        session.setAttribute("rollNumber", rollNumber);

        return ResponseEntity.ok(Map.of(
                "message", "Roll number updated successfully.",
                "rollNumber", rollNumber
        ));
    }

    /**
     * Helper resolving authenticated user ID by verifying the session's {@code apiToken} in MySQL.
     */
    private Long getAuthenticatedUserId(HttpSession session) {
        Object tokenAttribute = session.getAttribute("apiToken");

        if (!(tokenAttribute instanceof String token)
                || token.isBlank()) {
            return null;
        }

        return apiTokenService.findUserIdByToken(token)
                .orElse(null);
    }

    /** Helper constructing error response entities. */
    private ResponseEntity<Map<String, String>> error(
            HttpStatus status, String message) {
        return ResponseEntity.status(status).body(
                Map.of("message", message)
        );
    }

    /**
     * Changes the user's password, verifying their current password first, and immediately
     * issues a replacement token to preserve the active browser session.
     *
     * @param request JSON body containing current password and new password
     * @param session the user's HTTP session
     * @return 200 OK on success, 400 Bad Request on invalid credentials, or 401 Unauthorized
     */
    @PostMapping("/profile/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @RequestBody ChangePasswordRequest request,
            HttpSession session) {

        Long userId = getAuthenticatedUserId(session);

        if (userId == null) {
            return error(HttpStatus.UNAUTHORIZED,
                    "Your session has expired. Please sign in again.");
        }

        if (request.currentPassword() == null
                || request.newPassword() == null) {
            return error(HttpStatus.BAD_REQUEST,
                    "Please provide both your current and new passwords.");
        }

        try {
            authService.changePassword(
                    userId,
                    request.currentPassword(),
                    request.newPassword()
            );

            // The old token was revoked. Generate a fresh token so that
            // the current browser session remains authenticated.
            String newToken = apiTokenService.generateToken(userId);
            session.setAttribute("apiToken", newToken);

            return ResponseEntity.ok(Map.of(
                    "message", "Password changed successfully."
            ));

        } catch (IllegalArgumentException exception) {
            return error(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    /** DTO for password change requests. */
    public record ChangePasswordRequest(
            String currentPassword,
            String newPassword
    ) {}

    /** DTO for student name update requests. */
    public record ChangeNameRequest(String fullName) {}

    /** DTO for student roll number update requests. */
    public record ChangeRollNumberRequest(String rollNumber) {}
}