
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

/**
 * Controller handling web-based user authentication, session creation, logout, and session refreshing.
 *
 * <p>Security &amp; Session Protections:
 * <ul>
 *   <li><b>Hybrid Session-Token Architecture:</b> Signs in users against BCrypt credentials and issues
 *       a 5-day cryptographic Bearer token via {@link ApiTokenService}, embedding it within the server-side
 *       {@link HttpSession} as attribute {@code apiToken}.</li>
 *   <li><b>Session Fixation Prevention:</b> On every login, explicitly invalidates any preexisting session
 *       ({@code oldSession.invalidate()}) and revokes its associated token before allocating a fresh session ID.</li>
 *   <li><b>Compensating Token Rollback:</b> If session allocation or attribute binding fails after token generation,
 *       the newly minted database token is revoked immediately within a {@code try-catch} block to prevent dangling credentials.</li>
 *   <li><b>Open Redirect Protection:</b> Strict URL sanitization in {@link #validateReturnUrl(String)} blocks
 *       protocol-relative links ({@code //}), Windows backslashes ({@code \}), and CRLF injection characters
 *       to prevent phishing and HTTP response splitting.</li>
 * </ul>
 */
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

    /**
     * Authenticates student credentials and establishes a fresh, secure HTTP session.
     *
     * @param request JSON payload containing email and plaintext password
     * @param returnUrl optional target URL to redirect to after successful sign-in
     * @param httpRequest the servlet request for session manipulation
     * @return 200 OK with redirect destination, 400 Bad Request on missing fields, or 401 Unauthorized
     */
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

        // 1. Verify the credentials against BCrypt hash in MySQL.
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
            // 4. Revoke any previous website token in the existing session and defend against fixation.
            HttpSession oldSession = httpRequest.getSession(false);

            if (oldSession != null) {
                Object oldToken = oldSession.getAttribute("apiToken");

                if (oldToken instanceof String previousToken
                        && !previousToken.isBlank()) {
                    apiTokenService.revokeToken(previousToken);
                }

                // Invalidate old session to prevent session fixation attacks
                oldSession.invalidate();
            }

            // 5. Create a completely fresh website session.
            HttpSession session = httpRequest.getSession(true);

            session.setAttribute("apiToken", token);
            session.setAttribute("userId", user.get().id());
            session.setAttribute("userEmail", user.get().email());
            session.setAttribute("fullName", user.get().fullName());

            // 6. Sanitize return URL to prevent open redirect vulnerabilities.
            String destination = validateReturnUrl(returnUrl);

            return ResponseEntity.ok(Map.of(
                    "message", "Login successful.",
                    "redirectUrl", destination
            ));

        } catch (RuntimeException exception) {
            // Avoid leaving a usable token behind in MySQL if session setup fails.
            try {
                apiTokenService.revokeToken(token);
            } catch (RuntimeException ignored) {
                // The original failure remains the relevant error.
            }

            throw exception;
        }
    }

    /**
     * Terminates the current website session, revoking the associated database token
     * and redirecting the browser to the home page.
     *
     * @param request the servlet request
     * @return a {@link org.springframework.web.servlet.view.RedirectView} targeting {@code "/"}
     */
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

    /**
     * Programmatic endpoint allowing client-side scripts to verify and synchronize session data.
     *
     * @param request the servlet request containing the user's session
     * @return 200 OK if refreshed, or 401 Unauthorized if the session/token is invalid
     */
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

    /**
     * Sanitizes destination redirect URLs to ensure they are relative and free of header injection attacks.
     *
     * @param returnUrl the raw URL parameter provided by the client
     * @return a safe relative URL, defaulting to {@code "/"} if malformed
     */
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

    /** Request DTO containing credentials for web login. */
    public record LoginRequest(String email, String password) {}
}
