package com.campus.canteen.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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

        String sql = """
            INSERT INTO users (full_name, email, roll_number, password_hash)
            VALUES (?, ?, ?, ?)
            """;

        String passwordHash = passwordEncoder.encode(password);

        jdbcTemplate.update(
                sql,
                fullName.trim(),
                email.trim().toLowerCase(),
                rollNumber.trim(),
                passwordHash
        );
    }

    public boolean signIn(String email, String password) {
        String sql = """
            SELECT password_hash
            FROM users
            WHERE email = ?
            """;

        Optional<String> storedHash = jdbcTemplate.query(
                sql,
                rs -> rs.next()
                        ? Optional.of(rs.getString("password_hash"))
                        : Optional.empty(),
                email.trim().toLowerCase()
        );

        return storedHash
                .map(hash -> passwordEncoder.matches(password, hash))
                .orElse(false);
    }
}