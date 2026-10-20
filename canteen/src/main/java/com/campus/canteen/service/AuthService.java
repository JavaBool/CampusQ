package com.campus.canteen.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.sql.Timestamp;

@Service
public class AuthService {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.mail.from}")
    private String mailFrom;

    @Value("${app.password-reset.base-url}")
    private String resetBaseUrl;

    public AuthService(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            JavaMailSender mailSender) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
    }
    public Optional<UserDetails> findById(long id) {
        String sql = """
        SELECT id, full_name, email, roll_number
        FROM users
        WHERE id = ?
        """;

        return jdbcTemplate.query(sql, rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }

            return Optional.of(new UserDetails(
                    rs.getLong("id"),
                    rs.getString("full_name"),
                    rs.getString("email"),
                    rs.getString("roll_number")
            ));
        }, id);
    }

    public void requestPasswordReset(String email) {
        if (email == null || email.isBlank()) {
            return;
        }

        var user = findByEmail(email.trim());

        // Do not reveal whether an email is registered.
        if (user.isEmpty()) {
            return;
        }

        var account = user.get();

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);

        String rawToken = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        String tokenHash = hashResetToken(rawToken);
        Instant expiry = Instant.now().plus(30, ChronoUnit.MINUTES);

        jdbcTemplate.update("""
            UPDATE password_reset_tokens
            SET used_at = CURRENT_TIMESTAMP
            WHERE user_id = ? AND used_at IS NULL
            """, account.id());

        jdbcTemplate.update("""
            INSERT INTO password_reset_tokens
                (user_id, token_hash, expires_at)
            VALUES (?, ?, ?)
            """,
                account.id(),
                tokenHash,
                Timestamp.from(expiry)
        );

        String resetUrl = resetBaseUrl
                + "/auth/reset-password?token="
                + java.net.URLEncoder.encode(
                rawToken, StandardCharsets.UTF_8);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(account.email());
        message.setSubject("Reset your CampusBite password");
        message.setText("""
            We received a request to reset your CampusBite password.

            Use the link below within 30 minutes:
            %s

            If you did not request this, you can ignore this email.
            """.formatted(resetUrl));

        mailSender.send(message);
    }

    @Transactional
    public boolean resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()
                || newPassword == null || newPassword.length() < 8) {
            return false;
        }

        String tokenHash = hashResetToken(rawToken);

        var matchingUser = jdbcTemplate.query("""
            SELECT user_id
            FROM password_reset_tokens
            WHERE token_hash = ?
              AND used_at IS NULL
              AND expires_at > CURRENT_TIMESTAMP
            FOR UPDATE
            """,
                rs -> rs.next()
                        ? java.util.Optional.of(rs.getLong("user_id"))
                        : java.util.Optional.<Long>empty(),
                tokenHash
        );

        if (matchingUser.isEmpty()) {
            return false;
        }

        long userId = matchingUser.get();

        int consumed = jdbcTemplate.update("""
            UPDATE password_reset_tokens
            SET used_at = CURRENT_TIMESTAMP
            WHERE token_hash = ?
              AND used_at IS NULL
              AND expires_at > CURRENT_TIMESTAMP
            """, tokenHash);

        if (consumed != 1) {
            return false;
        }

        String passwordHash = passwordEncoder.encode(newPassword);

        jdbcTemplate.update("""
            UPDATE users
            SET password_hash = ?
            WHERE id = ?
            """, passwordHash, userId);

        // Revoke this user's v2 API tokens after a password reset.
        jdbcTemplate.update("""
            UPDATE api_tokens
            SET revoked_at = CURRENT_TIMESTAMP
            WHERE user_id = ? AND revoked_at IS NULL
            """, userId);

        // Make any other unused reset links for this user unusable.
        jdbcTemplate.update("""
            UPDATE password_reset_tokens
            SET used_at = CURRENT_TIMESTAMP
            WHERE user_id = ? AND used_at IS NULL
            """, userId);

        return true;
    }

    private String hashResetToken(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
    public boolean isPasswordResetTokenValid(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }

        String tokenHash = hashResetToken(rawToken);

        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM password_reset_tokens
                WHERE token_hash = ?
                  AND used_at IS NULL
                  AND expires_at > CURRENT_TIMESTAMP
                """,
                Integer.class,
                tokenHash
        );

        return count != null && count > 0;
    }
    @Transactional
    public void updateFullName(long userId, String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Full name cannot be empty.");
        }

        String normalizedName = fullName.trim();

        if (normalizedName.length() > 100) {
            throw new IllegalArgumentException(
                    "Full name cannot exceed 100 characters.");
        }

        int updated = jdbcTemplate.update(
                """
                UPDATE users
                SET full_name = ?
                WHERE id = ?
                """,
                normalizedName,
                userId
        );

        if (updated != 1) {
            throw new IllegalArgumentException("User account was not found.");
        }
    }

    @Transactional
    public void updateRollNumber(long userId, String rollNumber) {
        if (rollNumber == null || rollNumber.isBlank()) {
            throw new IllegalArgumentException(
                    "Roll number cannot be empty.");
        }

        String normalizedRollNumber = rollNumber.trim();

        if (normalizedRollNumber.length() > 50) {
            throw new IllegalArgumentException(
                    "Roll number cannot exceed 50 characters.");
        }

        try {
            int updated = jdbcTemplate.update(
                    """
                    UPDATE users
                    SET roll_number = ?
                    WHERE id = ?
                    """,
                    normalizedRollNumber,
                    userId
            );

            if (updated != 1) {
                throw new IllegalArgumentException(
                        "User account was not found.");
            }
        } catch (DataIntegrityViolationException exception) {
            // The users.roll_number column has a UNIQUE constraint.
            throw exception;
        }
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
    @Transactional
    public void changePassword(
            long userId,
            String currentPassword,
            String newPassword) {

        if (currentPassword == null || currentPassword.isBlank()) {
            throw new IllegalArgumentException(
                    "Please enter your current password.");
        }

        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException(
                    "The new password must contain at least 8 characters.");
        }

        String currentHash;

        try {
            currentHash = jdbcTemplate.queryForObject(
                    "SELECT password_hash FROM users WHERE id = ?",
                    String.class,
                    userId
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new IllegalArgumentException("User account was not found.");
        }

        if (!passwordEncoder.matches(currentPassword, currentHash)) {
            throw new IllegalArgumentException(
                    "Your current password is incorrect.");
        }

        String newHash = passwordEncoder.encode(newPassword);

        int updated = jdbcTemplate.update(
                "UPDATE users SET password_hash = ? WHERE id = ?",
                newHash,
                userId
        );

        if (updated != 1) {
            throw new IllegalArgumentException("Unable to update the password.");
        }

        // Revoke all existing API tokens for this user.
        jdbcTemplate.update(
                """
                UPDATE api_tokens
                SET revoked_at = CURRENT_TIMESTAMP
                WHERE user_id = ?
                  AND revoked_at IS NULL
                """,
                userId
        );
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