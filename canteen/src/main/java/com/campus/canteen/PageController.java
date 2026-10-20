
package com.campus.canteen;

import com.campus.canteen.service.AuthService;
import com.campus.canteen.service.SessionRefreshService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller mapping browser navigation paths to customer-facing Thymeleaf HTML templates.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Renders static and dynamic presentation views (home, cart, login, registration, password reset).</li>
 *   <li>Protects the {@code /profile} view by enforcing session authentication and refreshing
 *       user data via {@link SessionRefreshService#refresh(HttpSession)}.</li>
 *   <li>Pre-validates password reset tokens on {@code /auth/reset-password} to inform the view
 *       whether to render the password input form or an error prompt.</li>
 * </ul>
 */
@Controller
public class PageController {
    private final AuthService authService;
    private final SessionRefreshService sessionRefreshService;

    public PageController(AuthService authService, SessionRefreshService sessionRefreshService) {
        this.authService = authService;
        this.sessionRefreshService = sessionRefreshService;
    }

    /**
     * Renders the application home landing page.
     *
     * @return the {@code "index"} Thymeleaf template
     */
    @GetMapping("/")
    public String home() {
        return "index";
    }

    /**
     * Renders the client-side shopping cart page.
     *
     * @return the {@code "cart"} Thymeleaf template
     */
    @GetMapping("/cart")
    public String cart() {
        return "cart";
    }

    /**
     * Renders the student login form page.
     *
     * @return the {@code "login"} Thymeleaf template
     */
    @GetMapping("/auth/login")
    public String login(){
        return "login";
    }

    /**
     * Renders the student registration form page.
     *
     * @return the {@code "register"} Thymeleaf template
     */
    @GetMapping("/auth/register")
    public String register() {
        return "register";
    }

    /**
     * Renders the password recovery request form.
     *
     * @return the {@code "forgot-password"} Thymeleaf template
     */
    @GetMapping("/auth/forgot-password")
    public String forgotPasswordPage() {
        return "forgot-password";
    }

    /**
     * Renders the password reset submission form.
     *
     * <p>Pre-checks the validity of the {@code token} parameter to determine whether to display
     * the password input fields or an expired/invalid link notice.
     *
     * @param token the cryptographic reset token passed in query parameters
     * @param model Spring MVC model
     * @return the {@code "reset-password"} Thymeleaf template
     */
    @GetMapping("/auth/reset-password")
    public String resetPasswordPage(
            @RequestParam(required = false) String token,
            Model model) {

        boolean valid = authService.isPasswordResetTokenValid(token);

        model.addAttribute("invalidLink", !valid);

        if (valid) {
            model.addAttribute("token", token);
        }

        return "reset-password";
    }

    /**
     * Renders the authenticated student's profile page.
     *
     * <p>Enforces strict authentication: validates and refreshes session state against the database,
     * redirecting to login with a {@code returnUrl} parameter if unauthenticated or expired.
     *
     * @param request the current HTTP request
     * @param model Spring MVC model
     * @return the {@code "profile"} Thymeleaf template, or a redirect to login
     */
    @GetMapping("/profile")
    public String profile(
            HttpServletRequest request,
            Model model) {

        HttpSession session = request.getSession(false);

        if (session == null
                || !sessionRefreshService.refresh(session)) {
            return "redirect:/auth/login?returnUrl=/profile";
        }

        String email = (String) session.getAttribute("userEmail");

        if (email == null || email.isBlank()) {
            session.invalidate();
            return "redirect:/auth/login?returnUrl=/profile";
        }

        var user = authService.findByEmail(email);

        if (user.isEmpty()) {
            session.invalidate();
            return "redirect:/auth/login?returnUrl=/profile";
        }

        model.addAttribute("user", user.get());
        return "profile";
    }
}