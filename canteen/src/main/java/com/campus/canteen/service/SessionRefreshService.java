
package com.campus.canteen.service;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

/**
 * Service responsible for synchronizing web browser {@link HttpSession} state with the database.
 *
 * <p>Architectural role:
 * <ul>
 *   <li>Token-Backed Sessions: Web sessions store an {@code apiToken} attribute linking them to a database token record.</li>
 *   <li>Dynamic Invalidation: If the token is revoked (e.g. following a password change or logout elsewhere)
 *       or if the account is modified, this service terminates the session immediately.</li>
 *   <li>Attribute Freshness: Keeps session attributes ({@code userId}, {@code userEmail}, {@code fullName},
 *       {@code rollNumber}) up to date across page navigations without requiring re-login.</li>
 * </ul>
 */
@Service
public class SessionRefreshService {

    private final ApiTokenService apiTokenService;
    private final AuthService authService;

    public SessionRefreshService(
            ApiTokenService apiTokenService,
            AuthService authService) {
        this.apiTokenService = apiTokenService;
        this.authService = authService;
    }

    /**
     * Revalidates an HTTP session against the database token store and refreshes cached user profile attributes.
     *
     * <p>If the session lacks an {@code apiToken}, or if the token is expired, revoked, or unlinked from a user,
     * the session is actively invalidated to prevent unauthorized or stale access.
     *
     * @param session the HTTP session to validate and synchronize
     * @return {@code true} if the session was authenticated and updated with fresh database data;
     *         {@code false} if the session was invalid and invalidated
     */
    public boolean refresh(HttpSession session) {
        if (session == null) {
            return false;
        }

        Object tokenAttribute = session.getAttribute("apiToken");

        // Reject and invalidate sessions without a valid token attribute
        if (!(tokenAttribute instanceof String token) || token.isBlank()) {
            session.invalidate();
            return false;
        }

        // Verify token existence, expiration, and non-revocation in MySQL
        var tokenUserId = apiTokenService.findUserIdByToken(token);

        if (tokenUserId.isEmpty()) {
            session.invalidate();
            return false;
        }

        // Verify that the user account still exists in the database
        var user = authService.findById(tokenUserId.get());

        if (user.isEmpty()) {
            session.invalidate();
            return false;
        }

        var currentUser = user.get();

        // Refresh session attributes to reflect the latest profile data
        session.setAttribute("userId", currentUser.id());
        session.setAttribute("userEmail", currentUser.email());
        session.setAttribute("fullName", currentUser.fullName());
        session.setAttribute("rollNumber", currentUser.rollNumber());

        return true;
    }
}
