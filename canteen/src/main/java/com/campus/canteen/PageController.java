
package com.campus.canteen;

import com.campus.canteen.service.AuthService;
import com.campus.canteen.service.SessionRefreshService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PageController {
    private final AuthService authService;
    private final SessionRefreshService sessionRefreshService;

    public PageController(AuthService authService, SessionRefreshService sessionRefreshService) {
        this.authService = authService;
        this.sessionRefreshService = sessionRefreshService;
    }
    @GetMapping("/")
    public String home() {
        return "index";
    }
    @GetMapping("/cart")
    public String cart() {
        return "cart";
    }
    @GetMapping("/auth/login")
    public String login(){
        return "login";
    }
    @GetMapping("/auth/register")
    public String register() {
        return "register";
    }
    @GetMapping("/auth/forgot-password")
    public String forgotPasswordPage() {
        return "forgot-password";
    }

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