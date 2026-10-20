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

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

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
        HttpSession oldSession = httpRequest.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }

        HttpSession session = httpRequest.getSession(true);

        session.setAttribute("userId", user.id());
        session.setAttribute("userEmail", user.email());
        session.setAttribute("fullName", user.fullName());

        String destination = "/";

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
    public record RegisterRequest(
            String fullName,
            String email,
            String rollNumber,
            String password,
            String confirmPassword
    ) {}

    public record SignInRequest(
            String email,
            String password
    ) {}
}