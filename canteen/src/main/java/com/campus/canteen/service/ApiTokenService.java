
package com.campus.canteen.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;

/**
 * Service responsible for managing stateless Bearer API authentication tokens.
 *
 * <p>Security &amp; Cryptographic Architecture:
 * <ul>
 *   <li>Generates high-entropy 256-bit (32-byte) tokens using {@link SecureRandom}, formatted
 *       as URL-safe Base64 without padding for clean HTTP header transmission.</li>
 *   <li>Follows the principle of defense-in-depth: raw bearer tokens are never persisted in plaintext.
 *       Only their SHA-256 digests are stored in the database, preventing token replay attacks
 *       even if database dumps or read-only SQL injection vulnerabilities occur.</li>
 *   <li>Enforces a 5-day expiration lifetime and supports immediate revocation via timestamping.</li>
 * </ul>
 */
@Service
public class ApiTokenService {

    /** Default token validity period set to 5 days. */
    private static final long TOKEN_VALIDITY_DAYS = 5;

    private final JdbcTemplate jdbcTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiTokenService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Generates a new cryptographic API token for a user, records its SHA-256 digest
     * in the database with a 5-day expiration, and returns the raw plaintext token.
     *
     * @param userId the ID of the user acquiring the token
     * @return the raw URL-safe Base64 token to be returned to the client
     */
    @Transactional
    public String generateToken(long userId) {
        // 32 bytes (256 bits) of entropy provides collision resistance and brute-force protection
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);

        String rawToken = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        // Compute SHA-256 digest so the plaintext secret is never stored on disk
        String tokenHash = hashToken(rawToken);
        Instant expiresAt = Instant.now().plus(TOKEN_VALIDITY_DAYS, ChronoUnit.DAYS);

        jdbcTemplate.update("""
                INSERT INTO api_tokens (user_id, token_hash, expires_at)
                VALUES (?, ?, ?)
                """,
                userId,
                tokenHash,
                Timestamp.from(expiresAt)
        );

        return rawToken;
    }

    /**
     * Verifies whether a provided raw token matches an active, non-expired, and non-revoked record.
     *
     * @param rawToken the raw Bearer token presented in the request
     * @return {@code true} if the token digest exists and remains valid; {@code false} otherwise
     */
    public boolean isValid(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }

        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM api_tokens
                    WHERE token_hash = ?
                      AND revoked_at IS NULL
                      AND expires_at > CURRENT_TIMESTAMP
                )
                """,
                Boolean.class,
                hashToken(rawToken)
        ));
    }

    /**
     * Revokes an active token by timestamping its {@code revoked_at} column.
     *
     * <p>Any subsequent authentication attempts using this token will be rejected immediately.
     *
     * @param rawToken the raw Bearer token to invalidate
     * @return {@code true} if an active token record was found and marked revoked; {@code false} otherwise
     */
    @Transactional
    public boolean revokeToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }

        int updated = jdbcTemplate.update("""
                UPDATE api_tokens
                SET revoked_at = CURRENT_TIMESTAMP
                WHERE token_hash = ?
                  AND revoked_at IS NULL
                """,
                hashToken(rawToken)
        );

        return updated > 0;
    }

    /**
     * Computes the SHA-256 cryptographic hex digest of the raw token string.
     *
     * @param rawToken the plaintext token string
     * @return the hexadecimal representation of the SHA-256 hash
     * @throws IllegalStateException if the SHA-256 message digest algorithm is unavailable in the JVM
     */
    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    rawToken.getBytes(StandardCharsets.UTF_8)
            );

            return java.util.HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    /**
     * Resolves the user ID associated with a valid, non-expired, and non-revoked token.
     *
     * @param rawToken the raw Bearer token string
     * @return an {@link Optional} containing the user ID if valid, or {@link Optional#empty()}
     */
    public Optional<Long> findUserIdByToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }

        return jdbcTemplate.query("""
            SELECT user_id
            FROM api_tokens
            WHERE token_hash = ?
              AND revoked_at IS NULL
              AND expires_at > CURRENT_TIMESTAMP
            """,
                rs -> rs.next()
                        ? Optional.of(rs.getLong("user_id"))
                        : Optional.empty(),
                hashToken(rawToken)
        );
    }

}
