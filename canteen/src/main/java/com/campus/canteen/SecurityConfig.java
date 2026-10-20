
package com.campus.canteen;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration establishing the global HTTP filter chain.
 *
 * <p>Architectural Rationale:
 * <ul>
 *   <li>All HTTP endpoints are permitted at the Spring Security filter chain level ({@code permitAll()}),
 *       and default form/basic login and CSRF filters are disabled.</li>
 *   <li>This is a deliberate architectural design: CampusBite delegates authentication and access control
 *       to dedicated application-level mechanisms tailored for its distinct client types:
 *       <ol>
 *         <li><b>Thymeleaf Web Users:</b> Protected via HTTP session validation, {@link com.campus.canteen.config.SessionRefreshInterceptor},
 *             and controller-level authentication guards.</li>
 *         <li><b>Stateless REST API Clients (v2):</b> Authenticated via cryptographic Bearer tokens validated
 *             against hashed records in the database ({@link com.campus.canteen.service.ApiTokenService}).</li>
 *         <li><b>Administrator Portal:</b> Isolated entirely from user sessions, authenticating via an independent
 *             {@code ADMINSESSIONID} cookie mapped to an in-memory session registry.</li>
 *         <li><b>Simulated Payment Webhooks:</b> Payment callbacks ({@code /payment/pay}) must execute without
 *             session interference to mirror external payment gateway integrations.</li>
 *       </ol>
 *   </li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    /**
     * Configures the security filter chain to permit all incoming HTTP requests,
     * leaving authorization logic to controllers and interceptors.
     *
     * @param http the {@link HttpSecurity} builder
     * @return the configured {@link SecurityFilterChain}
     * @throws Exception if security configuration fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
                // Allow all routes through Spring Security's filter chain; custom security
                // is enforced at the controller and interceptor levels.
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                )
                // Disabling default CSRF allows stateless API clients and cross-origin forms/webhooks
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());

        return http.build();
    }
}