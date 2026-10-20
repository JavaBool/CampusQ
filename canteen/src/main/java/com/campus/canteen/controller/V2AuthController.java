package com.campus.canteen.controller;

import com.campus.canteen.service.ApiTokenService;
import com.campus.canteen.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
    private String extractBearerToken(String authorization) {
        if (authorization == null
                || !authorization.startsWith("Bearer ")) {
            return null;
        }

        String token = authorization.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    public record SignInRequest(String email, String password) {}
}