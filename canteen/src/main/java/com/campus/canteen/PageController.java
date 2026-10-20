
package com.campus.canteen;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

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
    @GetMapping("/auth/logout")
    public String logout(){
        return "logout";
    }
}