package com.campus.canteen.controller;

import com.campus.canteen.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * REST controller providing session-based authentication endpoints under API version 1 ({@code /api/v1/auth}).
 *
 * <p>Intended for browser AJAX requests and web frontend interactions that rely on standard HTTP cookies
 * and servlet sessions.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Registers a new student account, validating password confirmation and unique identity constraints.
     *
     * @param request JSON payload containing registration details and password confirmation
     * @param returnUrl optional redirect destination URL to preserve across sign-in
     * @return 201 Created with signin redirect URL, 400 Bad Request on validation error, or 409 Conflict on duplicate email/roll
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(
            @RequestBody RegisterRequest request, @RequestParam(required = false) String returnUrl) {

        if (request.fullName() == null || request.fullName().isBlank()
                || request.email() == null || request.email().isBlank()
                || request.rollNumber() == null || request.rollNumber().isBlank()
                || request.password() == null) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "Please fill in all required fields.")
            );
        }

        if (!request.password().equals(request.confirmPassword())) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "Passwords do not match.")
            );
        }

        if (request.password().length() < 8) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "Password must contain at least 8 characters.")
            );
        }

        try {
            authService.register(
                    request.fullName(),
                    request.email(),
                    request.rollNumber(),
                    request.password()
            );
            String destination = "/";

            // Sanitize returnUrl against open redirects and header injection
            if (returnUrl != null
                    && returnUrl.startsWith("/")
                    && !returnUrl.startsWith("//")
                    && !returnUrl.contains("\\")
                    && !returnUrl.contains("\r")
                    && !returnUrl.contains("\n")) {
                destination = returnUrl;
            }
            String redirectUrl = "/auth/login?returnUrl=" +
                    UriUtils.encodeQueryParam(destination, StandardCharsets.UTF_8);
            return ResponseEntity.status(HttpStatus.CREATED).body(
                    Map.of(
                            "message", "Account created successfully. Please sign in.",
                            "redirectUrl", redirectUrl
                    )
            );

        } catch (DuplicateKeyException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    Map.of("message", "That email or roll number is already registered.")
            );
        }
    }

    /**
     * Signs in a student, invalidating any preexisting session to mitigate session fixation,
     * and binds user attributes to a new HTTP session.
     *
     * @param request JSON payload with email and plaintext password
     * @param returnUrl optional target URL
     * @param httpRequest current HTTP request for session manipulation
     * @return 200 OK with sanitized redirectUrl, 400 Bad Request, or 401 Unauthorized
     */
    @PostMapping("/signin")
    public ResponseEntity<Map<String, String>> signIn(
            @RequestBody SignInRequest request, @RequestParam(required = false) String returnUrl, HttpServletRequest httpRequest) {

        if (request.email() == null || request.email().isBlank()
                || request.password() == null || request.password().isBlank()) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "Email and password are required.")
            );
        }

        boolean authenticated = authService.signIn(
                request.email(),
                request.password()
        );

        if (!authenticated) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Map.of("message", "Invalid email or password.")
            );
        }
        var user = authService.findByEmail(request.email())
                .orElseThrow();

        // Invalidate old session to mitigate session fixation attacks
        HttpSession oldSession = httpRequest.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }

        // Establish clean session with user attributes
        HttpSession session = httpRequest.getSession(true);

        session.setAttribute("userId", user.id());
        session.setAttribute("userEmail", user.email());
        session.setAttribute("fullName", user.fullName());

        String destination = "/";

        // Sanitize returnUrl to prevent open redirects
        if (returnUrl != null
                && returnUrl.startsWith("/")
                && !returnUrl.startsWith("//")
                && !returnUrl.contains("\\")
                && !returnUrl.contains("\r")
                && !returnUrl.contains("\n")) {
            destination = returnUrl;
        }

        return ResponseEntity.ok(Map.of(
                "message", "Login successful.",
                "redirectUrl", destination
        ));
    }

    /**
     * Invalidates the caller's HTTP session.
     *
     * @param httpRequest current HTTP request
     * @return 200 OK confirming logout
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            HttpServletRequest httpRequest) {

        HttpSession session = httpRequest.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        return ResponseEntity.ok(
                Map.of(
                        "message", "Logged out successfully.",
                        "redirectUrl", "/"
                )
        );
    }

    /**
     * Inspects the current HTTP session and returns the user's authentication status and identity details.
     *
     * @param session the user's HTTP session
     * @return 200 OK with authenticated user profile details, or 401 Unauthorized
     */
    @GetMapping("/session")
    public ResponseEntity<Map<String, Object>> getSession(
            HttpSession session) {

        Object userId = session.getAttribute("userId");

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Map.of("authenticated", false)
            );
        }

        return ResponseEntity.ok(
                Map.of(
                        "authenticated", true,
                        "userId", userId,
                        "userEmail", session.getAttribute("userEmail"),
                        "fullName", session.getAttribute("fullName")
                )
        );
    }

    /** DTO for student registration requests. */
    public record RegisterRequest(
            String fullName,
            String email,
            String rollNumber,
            String password,
            String confirmPassword
    ) {}

    /** DTO for student signin requests. */
    public record SignInRequest(
            String email,
            String password
    ) {}
}