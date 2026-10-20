
package com.campus.canteen.config;

import com.campus.canteen.service.SessionRefreshService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class SessionRefreshInterceptor implements HandlerInterceptor {

    private final SessionRefreshService sessionRefreshService;

    public SessionRefreshInterceptor(
            SessionRefreshService sessionRefreshService) {
        this.sessionRefreshService = sessionRefreshService;
    }

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
