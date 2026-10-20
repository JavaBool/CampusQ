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

/**
 * Core authentication and account management service for CampusBite.
 *
 * <p>Key Security Responsibilities:
 * <ul>
 *   <li>Credential Management: Passwords are protected using salted BCrypt hashing. Plaintext passwords
 *       are never logged, persisted, or returned in DTOs.</li>
 *   <li>Anti-Enumeration: Password reset requests return uniformly regardless of whether an email exists
 *       in the database, mitigating account-enumeration attacks.</li>
 *   <li>Secure Password Reset: Generates 256-bit cryptographically secure reset tokens stored exclusively
 *       as SHA-256 digests. Concurrency race conditions (token reuse) are prevented using pessimistic
 *       row locking ({@code FOR UPDATE}) inside transactional boundaries.</li>
 *   <li>Session Revocation Cascade: Changing or resetting a password automatically invalidates all
 *       active API bearer tokens associated with the account.</li>
 *   <li>Data Normalization: Normalizes email addresses using {@link Locale#ROOT} to prevent duplicate
 *       accounts or authentication bypass caused by Unicode or locale-specific casing anomalies.</li>
 * </ul>
 */
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

    /**
     * Looks up user details by primary key ID.
     *
     * @param id the user's primary key ID
     * @return an {@link Optional} containing {@link UserDetails} without sensitive password hashes,
     *         or {@link Optional#empty()} if no user was found
     */
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

    /**
     * Initiates a password reset workflow for the specified email.
     *
     * <p>Security considerations:
     * <ul>
     *   <li>Returns silently without error if the email does not exist, preventing user enumeration.</li>
     *   <li>Invalidates any prior unconsumed reset tokens for this user before issuing a new one.</li>
     *   <li>Issues a 32-byte cryptographic token with a 30-minute expiration window, storing only
     *       its SHA-256 digest in the database.</li>
     * </ul>
     *
     * @param email the email address requesting a password reset
     */
    public void requestPasswordReset(String email) {
        if (email == null || email.isBlank()) {
            return;
        }

        var user = findByEmail(email.trim());

        // Do not reveal whether an email is registered to prevent account enumeration.
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

        // Invalidate any previously issued, unconsumed reset tokens for this user
        jdbcTemplate.update("""
            UPDATE password_reset_tokens
            SET used_at = CURRENT_TIMESTAMP
            WHERE user_id = ? AND used_at IS NULL
            """, account.id());

        // Persist token digest with 30-minute expiration
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

    /**
     * Atomically validates a password reset token, updates the user's password, and revokes
     * existing sessions and active reset tokens.
     *
     * <p>Pessimistic row locking ({@code FOR UPDATE}) ensures that concurrent requests presenting
     * the same reset token cannot execute in parallel to cause race conditions.
     *
     * @param rawToken the raw reset token received from the client
     * @param newPassword the replacement plaintext password (minimum 8 characters)
     * @return {@code true} if the token was valid, unconsumed, and successfully redeemed; {@code false} otherwise
     */
    @Transactional
    public boolean resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()
                || newPassword == null || newPassword.length() < 8) {
            return false;
        }

        String tokenHash = hashResetToken(rawToken);

        // Pessimistic lock on the token record prevents concurrent redemption race conditions
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

        // Atomically mark the reset token as consumed
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

        // Security cascade: Revoke this user's v2 API tokens after a password reset
        jdbcTemplate.update("""
            UPDATE api_tokens
            SET revoked_at = CURRENT_TIMESTAMP
            WHERE user_id = ? AND revoked_at IS NULL
            """, userId);

        // Make any other unused reset links for this user unusable
        jdbcTemplate.update("""
            UPDATE password_reset_tokens
            SET used_at = CURRENT_TIMESTAMP
            WHERE user_id = ? AND used_at IS NULL
            """, userId);

        return true;
    }

    /**
     * Computes the SHA-256 hex digest of a raw password reset token.
     *
     * @param token the plaintext reset token
     * @return the hexadecimal digest
     * @throws IllegalStateException if SHA-256 algorithm is missing
     */
    private String hashResetToken(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    /**
     * Validates whether a given reset token is currently unexpired and unused.
     *
     * @param rawToken the raw token string
     * @return {@code true} if valid and ready for consumption; {@code false} otherwise
     */
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

    /**
     * Updates the full name for a specific user account.
     *
     * @param userId the ID of the user
     * @param fullName the new full name (1 to 100 characters)
     * @throws IllegalArgumentException if the name is blank, exceeds 100 characters, or user not found
     */
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

    /**
     * Updates the unique roll number for a user account.
     *
     * @param userId the ID of the user
     * @param rollNumber the new unique roll number (1 to 50 characters)
     * @throws IllegalArgumentException if the roll number is blank, exceeds 50 characters, or user not found
     * @throws DataIntegrityViolationException if the roll number violates unique constraints
     */
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

    /**
     * Registers a new student account with a BCrypt-hashed password.
     *
     * @param fullName the user's full name
     * @param email the user's email address (normalized to lowercase)
     * @param rollNumber the user's unique academic roll number
     * @param password the plaintext password (minimum 8 characters)
     * @throws IllegalArgumentException if required fields are missing or password is under 8 characters
     * @throws DuplicateKeyException if email or roll number is already registered
     */
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

        // Salting and hashing are performed automatically by BCrypt
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

    /**
     * Verifies user login credentials by checking BCrypt password hashes.
     *
     * @param email the user's email address
     * @param password the candidate plaintext password
     * @return {@code true} if credentials match; {@code false} otherwise
     */
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
     *
     * @param email the user's email address
     * @return an {@link Optional} containing public user details, or {@link Optional#empty()}
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

    /**
     * Normalizes an email address by trimming whitespace and converting to lowercase
     * using {@link Locale#ROOT} to prevent locale-dependent character mapping issues.
     *
     * @param email raw email string
     * @return normalized lowercase email
     */
    private String normalizeEmail(String email) {
        return requireText(email, "Email")
                .toLowerCase(Locale.ROOT);
    }

    /**
     * Changes an authenticated user's password, verifying their existing password first,
     * and invalidating all active API bearer tokens.
     *
     * @param userId the ID of the user
     * @param currentPassword the user's current plaintext password
     * @param newPassword the new plaintext password (minimum 8 characters)
     * @throws IllegalArgumentException if current password is wrong, new password is under 8 chars,
     *                                  or account is not found
     */
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

        // Security cascade: Invalidate all existing API tokens for this user on password update
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

    /**
     * Validates that a required text field is non-null and not blank, returning its trimmed value.
     */
    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " is required.");
        }

        return value.trim();
    }

    /**
     * Public representation of user account details excluding sensitive credentials.
     *
     * @param id the unique primary key of the user
     * @param fullName the display name of the user
     * @param email the registered email address
     * @param rollNumber the academic student roll number
     */
    public record UserDetails(
            long id,
            String fullName,
            String email,
            String rollNumber) {
    }
}