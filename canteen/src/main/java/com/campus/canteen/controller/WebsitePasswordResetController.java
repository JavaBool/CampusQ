package com.campus.canteen.controller;

import com.campus.canteen.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controller handling password recovery and reset endpoints for students.
 *
 * <p>Key Security Protections:
 * <ul>
 *   <li><b>User Enumeration Defense:</b> The {@code forgotPassword} endpoint deliberately returns the exact same
 *       HTTP 200 OK response whether the account exists or not, and catches mail transport errors without exposing
 *       them to the caller, preventing external attackers from enumerating valid student emails.</li>
 *   <li><b>Secure Reset Execution:</b> Validates password complexity constraints and delegates to
 *       {@link AuthService#resetPassword(String, String)}, which locks the token row with {@code FOR UPDATE},
 *       hashes the new password with BCrypt, and invalidates all active sessions.</li>
 * </ul>
 */
@RestController
public class WebsitePasswordResetController {

    private static final Logger logger =
            LoggerFactory.getLogger(WebsitePasswordResetController.class);

    private final AuthService authService;

    public WebsitePasswordResetController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Accepts a student's email and initiates password reset link delivery.
     *
     * <p>Always returns 200 OK with a generic message to prevent account enumeration.
     *
     * @param request JSON payload containing the user's email address
     * @return 200 OK with standard status message, or 400 Bad Request if email is empty
     */
    @PostMapping("/auth/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @RequestBody ForgotPasswordRequest request) {

        if (request.email() == null || request.email().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Please enter your email address."
                    ));
        }

        try {
            authService.requestPasswordReset(request.email());
        } catch (MailException exception) {
            // Avoid revealing whether the email belongs to an account or if SMTP delivery failed.
            logger.error("Failed to send a password-reset email.", exception);
        }

        // Use the same response whether or not the account exists.
        return ResponseEntity.ok(Map.of(
                "message",
                "If an account exists for that email, a password-reset link will be sent."
        ));
    }

    /**
     * Executes the password change using a valid, unconsumed password reset token.
     *
     * @param request JSON payload containing the cryptographic token and new password (min 8 characters)
     * @return 200 OK on success with login redirect, or 400 Bad Request on invalid/expired token or short password
     */
    @PostMapping("/auth/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @RequestBody ResetPasswordRequest request) {

        if (request.token() == null || request.token().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "The reset token is missing. Request a new link."
            ));
        }

        if (request.newPassword() == null
                || request.newPassword().length() < 8) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Password must contain at least 8 characters."
            ));
        }

        boolean reset = authService.resetPassword(
                request.token(),
                request.newPassword()
        );

        if (!reset) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "message",
                    "The reset link is invalid, expired, or has already been used. Request a new link."
            ));
        }

        return ResponseEntity.ok(Map.of(
                "message", "Password reset successfully.",
                "redirectUrl", "/auth/login"
        ));
    }

    /** DTO for requesting a password reset email. */
    public record ForgotPasswordRequest(String email) {}

    /** DTO for executing a password reset with a token. */
    public record ResetPasswordRequest(
            String token,
            String newPassword
    ) {}
}