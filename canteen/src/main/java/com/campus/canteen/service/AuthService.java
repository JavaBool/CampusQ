package com.campus.canteen.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class AuthService {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    public void register(
            String fullName,
            String email,
            String rollNumber,
            String password) {

        String normalizedName = requireText(fullName, "Full name");
        String normalizedEmail = normalizeEmail(email);
        String normalizedRollNumber =
                requireText(rollNumber, "Roll number");

        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException(
                    "Password must contain at least 8 characters.");
        }

        String passwordHash = passwordEncoder.encode(password);

        String sql = """
                INSERT INTO users
                    (full_name, email, roll_number, password_hash)
                VALUES (?, ?, ?, ?)
                """;

        jdbcTemplate.update(
                sql,
                normalizedName,
                normalizedEmail,
                normalizedRollNumber,
                passwordHash
        );
    }

    public boolean signIn(String email, String password) {
        if (email == null || email.isBlank()
                || password == null || password.isEmpty()) {
            return false;
        }

        String sql = """
                SELECT password_hash
                FROM users
                WHERE email = ?
                """;

        Optional<String> storedHash = jdbcTemplate.query(
                sql,
                rs -> rs.next()
                        ? Optional.ofNullable(
                        rs.getString("password_hash"))
                        : Optional.empty(),
                normalizeEmail(email)
        );

        return storedHash
                .map(hash -> passwordEncoder.matches(password, hash))
                .orElse(false);
    }
    /**
     * Retrieves the user's public account details after authentication.
     * Never returns the password hash.
     */
    public Optional<UserDetails> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }

        String sql = """
                SELECT id, full_name, email, roll_number
                FROM users
                WHERE email = ?
                """;

        return jdbcTemplate.query(
                sql,
                rs -> {
                    if (!rs.next()) {
                        return Optional.empty();
                    }

                    return Optional.of(new UserDetails(
                            rs.getLong("id"),
                            rs.getString("full_name"),
                            rs.getString("email"),
                            rs.getString("roll_number")
                    ));
                },
                normalizeEmail(email)
        );
    }

    private String normalizeEmail(String email) {
        return requireText(email, "Email")
                .toLowerCase(Locale.ROOT);
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " is required.");
        }

        return value.trim();
    }

    public record UserDetails(
            long id,
            String fullName,
            String email,
            String rollNumber) {
    }
}