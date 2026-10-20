
package com.campus.canteen.controller;

import com.campus.canteen.service.ApiTokenService;
import com.campus.canteen.service.AuthService;
import com.campus.canteen.service.SessionRefreshService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class WebsiteAuthController {
    private final SessionRefreshService sessionRefreshService;
    private final AuthService authService;
    private final ApiTokenService apiTokenService;

    public WebsiteAuthController(
            AuthService authService,
            ApiTokenService apiTokenService,
            SessionRefreshService sessionRefreshService) {
        this.authService = authService;
        this.apiTokenService = apiTokenService;
        this.sessionRefreshService = sessionRefreshService;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<Map<String, String>> login(
            @RequestBody LoginRequest request,
            @RequestParam(required = false) String returnUrl,
            HttpServletRequest httpRequest) {

        if (request.email() == null || request.email().isBlank()
                || request.password() == null || request.password().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "Email and password are required.")
            );
        }

        // 1. Verify the credentials.
        if (!authService.signIn(request.email(), request.password())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Map.of("message", "Invalid email or password.")
            );
        }

        // 2. Retrieve the authenticated user's details.
        var user = authService.findByEmail(request.email());

        if (user.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Map.of("message", "User account not found.")
            );
        }

        // 3. Generate a five-day v2 API token.
        String token = apiTokenService.generateToken(user.get().id());

        try {
            // 4. Revoke any previous website token in the existing session.
            HttpSession oldSession = httpRequest.getSession(false);

            if (oldSession != null) {
                Object oldToken = oldSession.getAttribute("apiToken");

                if (oldToken instanceof String previousToken
                        && !previousToken.isBlank()) {
                    apiTokenService.revokeToken(previousToken);
                }

                // Prevent session fixation.
                oldSession.invalidate();
            }

            // 5. Create a fresh website session.
            HttpSession session = httpRequest.getSession(true);

            session.setAttribute("apiToken", token);
            session.setAttribute("userId", user.get().id());
            session.setAttribute("userEmail", user.get().email());
            session.setAttribute("fullName", user.get().fullName());

            // 6. Preserve the return-to-origin behaviour.
            String destination = validateReturnUrl(returnUrl);

            return ResponseEntity.ok(Map.of(
                    "message", "Login successful.",
                    "redirectUrl", destination
            ));

        } catch (RuntimeException exception) {
            // Avoid leaving a usable token behind if session setup fails.
            try {
                apiTokenService.revokeToken(token);
            } catch (RuntimeException ignored) {
                // The original failure remains the relevant error.
            }

            throw exception;
        }
    }

    @GetMapping("/auth/logout")
    public org.springframework.web.servlet.view.RedirectView logout(
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session != null) {
            Object token = session.getAttribute("apiToken");

            try {
                if (token instanceof String apiToken && !apiToken.isBlank()) {
                    apiTokenService.revokeToken(apiToken);
                }
            } finally {
                session.invalidate();
            }
        }

        return new org.springframework.web.servlet.view.RedirectView("/");
    }

    @GetMapping("/auth/refreshSession")
    public ResponseEntity<Map<String, Object>> refreshSession(
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        boolean updated = sessionRefreshService.refresh(session);

        if (!updated) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "updated", false,
                            "message", "Session or token is invalid or expired."
                    ));
        }

        return ResponseEntity.ok(Map.of(
                "updated", true,
                "message", "Session details refreshed successfully."
        ));
    }

    private String validateReturnUrl(String returnUrl) {
        if (returnUrl == null
                || !returnUrl.startsWith("/")
                || returnUrl.startsWith("//")
                || returnUrl.contains("\\")
                || returnUrl.contains("\r")
                || returnUrl.contains("\n")) {
            return "/";
        }

        return returnUrl;
    }

    public record LoginRequest(String email, String password) {}
}
