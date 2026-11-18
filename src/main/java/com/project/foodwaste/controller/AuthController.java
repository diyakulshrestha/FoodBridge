package com.project.foodwaste.controller;

import com.project.foodwaste.model.FoodDonation;
import com.project.foodwaste.model.User;
import com.project.foodwaste.repository.DonationRepository;
import com.project.foodwaste.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DonationRepository donationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping("/")
    public String home(Model model) {
        List<FoodDonation> delivered = donationRepository.findByStatus("DELIVERED");
        int totalMeals = 0;
        for (FoodDonation d : delivered) {
            String digits = d.getQuantity().replaceAll("[^0-9]", "");
            totalMeals += digits.isEmpty() ? 10 : Integer.parseInt(digits);
        }
        double foodSavedKg = Math.round(totalMeals * 0.4 * 10.0) / 10.0;
        double co2PreventedKg = Math.round(foodSavedKg * 2.5 * 10.0) / 10.0;

        model.addAttribute("totalDeliveries", delivered.size());
        model.addAttribute("totalMeals", totalMeals);
        model.addAttribute("foodSavedKg", foodSavedKg);
        model.addAttribute("co2PreventedKg", co2PreventedKg);
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        model.addAttribute("user", new User());
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(@ModelAttribute User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userRepository.save(user);
        return "redirect:/login?registered";
    }

    @GetMapping("/dashboard")
    public String defaultDashboard(Authentication authentication) {
        String role = authentication.getAuthorities().iterator().next().getAuthority();
        return switch (role) {
            case "ROLE_DONOR" -> "redirect:/donor/dashboard";
            case "ROLE_NGO" -> "redirect:/ngo/dashboard";
            case "ROLE_VOLUNTEER" -> "redirect:/volunteer/dashboard";
            case "ROLE_ADMIN" -> "redirect:/admin/dashboard";
            default -> "redirect:/";
        };
    }
}
