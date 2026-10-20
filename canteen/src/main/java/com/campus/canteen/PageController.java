
package com.campus.canteen;

import com.campus.canteen.service.AuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.ui.Model;

@Controller
public class PageController {
    private final AuthService authService;

    public PageController(AuthService authService) {
        this.authService = authService;
    }
    @GetMapping("/")
    public String home() {
        return "index";
    }
    @GetMapping("/menu")
    public String menu() {
        return "menu";
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
    @GetMapping("/profile")
    public String profile(
            HttpSession session,
            Model model) {

        if (session.getAttribute("userId") == null) {
            return "redirect:/auth/signin";
        }

        String email = (String) session.getAttribute("userEmail");

        if (email == null || email.isBlank()) {
            session.invalidate();
            return "redirect:/auth/signin";
        }

        var user = authService.findByEmail(email);

        if (user.isEmpty()) {
            session.invalidate();
            return "redirect:/auth/signin";
        }

        model.addAttribute("user", user.get());
        return "profile";
    }
}