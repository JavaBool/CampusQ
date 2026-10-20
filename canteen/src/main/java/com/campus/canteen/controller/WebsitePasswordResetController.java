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

@RestController
public class WebsitePasswordResetController {

    private static final Logger logger =
            LoggerFactory.getLogger(WebsitePasswordResetController.class);

    private final AuthService authService;

    public WebsitePasswordResetController(AuthService authService) {
        this.authService = authService;
    }

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
            // Avoid revealing whether the email belongs to an account.
            logger.error("Failed to send a password-reset email.", exception);
        }

        // Use the same response whether or not the account exists.
        return ResponseEntity.ok(Map.of(
                "message",
                "If an account exists for that email, a password-reset link will be sent."
        ));
    }

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

    public record ForgotPasswordRequest(String email) {}

    public record ResetPasswordRequest(
            String token,
            String newPassword
    ) {}
}