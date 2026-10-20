
package com.campus.canteen.service;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

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
     * Refreshes the website session using the latest database details.
     *
     * @return true if the session was successfully refreshed;
     *         false if the session or its token is invalid.
     */
    public boolean refresh(HttpSession session) {
        if (session == null) {
            return false;
        }

        Object tokenAttribute = session.getAttribute("apiToken");

        if (!(tokenAttribute instanceof String token) || token.isBlank()) {
            session.invalidate();
            return false;
        }

        var tokenUserId = apiTokenService.findUserIdByToken(token);

        if (tokenUserId.isEmpty()) {
            session.invalidate();
            return false;
        }

        var user = authService.findById(tokenUserId.get());

        if (user.isEmpty()) {
            session.invalidate();
            return false;
        }

        var currentUser = user.get();

        session.setAttribute("userId", currentUser.id());
        session.setAttribute("userEmail", currentUser.email());
        session.setAttribute("fullName", currentUser.fullName());
        session.setAttribute("rollNumber", currentUser.rollNumber());

        return true;
    }
}
