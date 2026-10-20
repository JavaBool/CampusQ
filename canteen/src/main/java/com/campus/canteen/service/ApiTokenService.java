
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

@Service
public class ApiTokenService {

    private static final long TOKEN_VALIDITY_DAYS = 5;

    private final JdbcTemplate jdbcTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiTokenService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public String generateToken(long userId) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);

        String rawToken = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

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
