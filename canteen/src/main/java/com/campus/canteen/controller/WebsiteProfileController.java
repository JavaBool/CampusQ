
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

    private Long getAuthenticatedUserId(HttpSession session) {
        Object tokenAttribute = session.getAttribute("apiToken");

        if (!(tokenAttribute instanceof String token)
                || token.isBlank()) {
            return null;
        }

        return apiTokenService.findUserIdByToken(token)
                .orElse(null);
    }

    private ResponseEntity<Map<String, String>> error(
            HttpStatus status, String message) {
        return ResponseEntity.status(status).body(
                Map.of("message", message)
        );
    }
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

    public record ChangePasswordRequest(
            String currentPassword,
            String newPassword
    ) {}
    public record ChangeNameRequest(String fullName) {}

    public record ChangeRollNumberRequest(String rollNumber) {}
}