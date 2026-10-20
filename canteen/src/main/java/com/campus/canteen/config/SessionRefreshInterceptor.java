
package com.campus.canteen.config;

import com.campus.canteen.service.SessionRefreshService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * HandlerInterceptor that inspects and refreshes active user sessions on designated routes.
 *
 * <p>Design &amp; Security Decisions:
 * <ul>
 *   <li>Non-Blocking for Guests: Requests without an existing session pass through unimpeded,
 *       ensuring public browsing (e.g., viewing the home page or menu) is never disrupted.</li>
 *   <li>Stale Session Invalidation: If an active session contains an invalid, expired, or revoked token,
 *       {@link SessionRefreshService#refresh(HttpSession)} immediately invalidates the session in-place.</li>
 *   <li>Graceful Degradation: Even when a stale session is invalidated, this interceptor returns {@code true}
 *       so public views still render normally as guest views. Protected pages (e.g. {@code /profile})
 *       enforce mandatory authentication within their respective controllers.</li>
 * </ul>
 */
@Component
public class SessionRefreshInterceptor implements HandlerInterceptor {

    private final SessionRefreshService sessionRefreshService;

    public SessionRefreshInterceptor(
            SessionRefreshService sessionRefreshService) {
        this.sessionRefreshService = sessionRefreshService;
    }

    /**
     * Intercepts incoming requests before controller execution to revalidate and update session attributes.
     *
     * @param request the current HTTP servlet request
     * @param response the current HTTP servlet response
     * @param handler the chosen handler object to execute
     * @return {@code true} to proceed with handler execution
     */
    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {

        HttpSession session = request.getSession(false);

        // Public visitors without a session can access the page normally.
        if (session == null) {
            return true;
        }

        // Refresh valid sessions. Invalid sessions are cleared by the service.
        sessionRefreshService.refresh(session);

        // Do not block public pages if the session is invalid.
        // Protected page controllers can handle authentication separately.
        return true;
    }
}
