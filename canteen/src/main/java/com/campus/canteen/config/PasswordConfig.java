package com.campus.canteen.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Spring configuration providing the password hashing bean for the application.
 */
@Configuration
public class PasswordConfig {

    /**
     * Provides a {@link BCryptPasswordEncoder} for securely hashing and verifying passwords.
     *
     * <p>BCrypt automatically incorporates a random salt into each hash and uses an adaptive
     * work factor that increases resistance to brute-force offline cracking attacks.
     *
     * @return a thread-safe {@link PasswordEncoder} instance
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}