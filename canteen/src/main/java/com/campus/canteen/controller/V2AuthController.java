package com.campus.canteen.controller;

import com.campus.canteen.service.ApiTokenService;
import com.campus.canteen.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller implementing stateless Bearer token authentication under API version 2 ({@code /api/v2/auth}).
 *
 * <p>Designed for mobile clients, external integrations, and single-page applications that do not use cookies.
 * Authentication state is maintained via cryptographically random Bearer tokens managed by {@link ApiTokenService}.
 */
@RestController
@RequestMapping("/api/v2/auth")
public class V2AuthController {

    private final AuthService authService;
    private final ApiTokenService apiTokenService;

    public V2AuthController(
            AuthService authService,
            ApiTokenService apiTokenService) {
        this.authService = authService;
        this.apiTokenService = apiTokenService;
    }

    /**
     * Authenticates user credentials and issues a stateless 5-day Bearer token.
     *
     * @param request JSON payload containing email and plaintext password
     * @return 200 OK with Bearer token, 400 Bad Request on missing fields, or 401 Unauthorized
     */
    // POST /api/v2/auth/login
    @PostMapping("/login")
    public ResponseEntity<?> signIn(@RequestBody SignInRequest request) {

        if (request.email() == null || request.email().isBlank()
                || request.password() == null || request.password().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Email and password are required."));
        }

        if (!authService.signIn(request.email(), request.password())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid email or password."));
        }

        var user = authService.findByEmail(request.email())
                .orElseThrow();

        String token = apiTokenService.generateToken(user.id());

        // Return the token only. Never return the stored hash.
        return ResponseEntity.ok(Map.of("token", token));
    }

    /**
     * Validates an incoming Bearer token against database digests, checking expiration and revocation.
     *
     * @param authorization the {@code Authorization: Bearer <token>} header
     * @return 200 OK with {@code {"valid": true|false}}
     */
    // POST /api/v2/auth/validate
    @PostMapping("/validate")
    public ResponseEntity<Map<String, Boolean>> validate(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            ) String authorization) {

        String token = extractBearerToken(authorization);
        boolean valid = token != null && apiTokenService.isValid(token);

        return ResponseEntity.ok(Map.of("valid", valid));
    }

    /**
     * Revokes a Bearer token in the database, immediately terminating its validity.
     *
     * @param authorization the {@code Authorization: Bearer <token>} header
     * @return 200 OK if successfully revoked, 400 Bad Request if missing, or 401 Unauthorized if invalid
     */
    // POST /api/v2/auth/logout
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Boolean>> logout(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            ) String authorization) {

        String token = extractBearerToken(authorization);

        if (token == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false));
        }

        boolean revoked = apiTokenService.revokeToken(token);

        if (!revoked) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false));
        }

        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * Helper to extract the token substring from a Bearer Authorization header.
     */
    private String extractBearerToken(String authorization) {
        if (authorization == null
                || !authorization.startsWith("Bearer ")) {
            return null;
        }

        String token = authorization.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    /** DTO for API v2 signin requests. */
    public record SignInRequest(String email, String password) {}
}