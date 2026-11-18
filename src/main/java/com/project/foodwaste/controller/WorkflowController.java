package com.project.foodwaste.controller;

import com.project.foodwaste.model.FoodDonation;
import com.project.foodwaste.model.User;
import com.project.foodwaste.repository.DonationRepository;
import com.project.foodwaste.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Controller
public class WorkflowController {

    @Autowired
    private DonationRepository donationRepository;

    @Autowired
    private UserRepository userRepository;

    private User getLoggedInUser(Authentication auth) {
        return userRepository.findByEmail(auth.getName()).orElseThrow();
    }

    private String generateOtp() {
        return String.format("%04d", new Random().nextInt(10000));
    }

    // 1. AUTOMATIC EXPIRY SAFETY LOCK (Flags expired items so spoiled food cannot be requested)
    private void lockExpiredDonations() {
        LocalDateTime now = LocalDateTime.now();
        List<FoodDonation> activeItems = donationRepository.findAll().stream()
                .filter(d -> d.getStatus().equals("AVAILABLE") || d.getStatus().equals("REQUESTED"))
                .filter(d -> d.getAvailableUntil() != null && d.getAvailableUntil().isBefore(now))
                .collect(Collectors.toList());

        if (!activeItems.isEmpty()) {
            activeItems.forEach(d -> d.setStatus("EXPIRED"));
            donationRepository.saveAll(activeItems);
        }
    }

    // 2. ENVIRONMENTAL & SOCIAL IMPACT CALCULATOR
    private Map<String, Object> calculateImpactMetrics() {
        List<FoodDonation> delivered = donationRepository.findByStatus("DELIVERED");
        int totalMeals = 0;
        Pattern numberPattern = Pattern.compile("\\d+");

        for (FoodDonation d : delivered) {
            if (d.getQuantity() != null) {
                Matcher m = numberPattern.matcher(d.getQuantity());
                if (m.find()) {
                    totalMeals += Integer.parseInt(m.group());
                } else {
                    totalMeals += 10;
                }
            }
        }

        double foodSavedKg = Math.round(totalMeals * 0.4 * 10.0) / 10.0;
        double co2PreventedKg = Math.round(foodSavedKg * 2.5 * 10.0) / 10.0;

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalDeliveries", delivered.size());
        stats.put("totalMeals", totalMeals);
        stats.put("foodSavedKg", foodSavedKg);
        stats.put("co2PreventedKg", co2PreventedKg);
        return stats;
    }

    // 3. VOLUNTEER GAMIFICATION LEADERBOARD
    private List<User> getVolunteerLeaderboard() {
        return userRepository.findByRole("ROLE_VOLUNTEER").stream()
                .sorted(Comparator.comparingInt(User::getPoints).reversed())
                .collect(Collectors.toList());
    }

    // --- DONOR WORKFLOW & IMPACT CERTIFICATE ---
    @GetMapping("/donor/dashboard")
    public String donorDashboard(Model model, Authentication auth) {
        lockExpiredDonations();
        User donor = getLoggedInUser(auth);
        model.addAttribute("user", donor);
        model.addAttribute("newDonation", new FoodDonation());
        model.addAttribute("donations", donationRepository.findByDonor(donor));
        return "donor-dashboard";
    }

    @PostMapping("/donor/donate")
    public String createDonation(@ModelAttribute FoodDonation donation,
                                 @RequestParam(defaultValue = "4") int validHours,
                                 Authentication auth) {
        donation.setDonor(getLoggedInUser(auth));
        LocalDateTime now = LocalDateTime.now();
        donation.setPreparationTime(now);
        donation.setAvailableUntil(now.plusHours(validHours));
        donation.setStatus("AVAILABLE");
        donationRepository.save(donation);
        return "redirect:/donor/dashboard";
    }
    
    @PostMapping("/donor/approve/{id}")
    public String approveDonation(@PathVariable Long id,
                                  Authentication auth,
                                  RedirectAttributes redirectAttributes) {

        FoodDonation donation = donationRepository.findById(id).orElseThrow();
        User donor = getLoggedInUser(auth);

        // Check whether this donation belongs to the logged-in donor
        if (donation.getDonor() == null ||
            !Objects.equals(donation.getDonor().getId(), donor.getId())) {

            redirectAttributes.addFlashAttribute("errorMsg",
                    "You are not allowed to approve this donation.");
            return "redirect:/donor/dashboard";
        }

        // Only REQUESTED donations can be approved
        if (!"REQUESTED".equals(donation.getStatus())) {

            redirectAttributes.addFlashAttribute("errorMsg",
                    "This donation is no longer awaiting approval.");
            return "redirect:/donor/dashboard";
        }

        donation.setStatus("APPROVED");
        donationRepository.save(donation);

        redirectAttributes.addFlashAttribute("successMsg",
                "NGO request approved successfully.");

        return "redirect:/donor/dashboard";
    }

    @GetMapping("/donor/certificate/{id}")
    public String viewCertificate(@PathVariable Long id, Model model, Authentication auth) {
        FoodDonation donation = donationRepository.findById(id).orElseThrow();
        User donor = getLoggedInUser(auth);
        if (!donation.getDonor().getId().equals(donor.getId()) || !donation.getStatus().equals("DELIVERED")) {
            return "redirect:/donor/dashboard";
        }
        model.addAttribute("donation", donation);
        return "certificate";
    }

    // --- NGO WORKFLOW & DIETARY/LOCATION SEARCH FILTER ---
    @GetMapping("/ngo/dashboard")
    public String ngoDashboard(@RequestParam(required = false) String keyword, Model model, Authentication auth) {
        lockExpiredDonations();
        User ngo = getLoggedInUser(auth);

        List<FoodDonation> available = donationRepository.findByStatus("AVAILABLE");
        if (keyword != null && !keyword.isBlank()) {
            String search = keyword.toLowerCase().trim();
            available = available.stream()
                    .filter(d -> d.getFoodType().toLowerCase().contains(search)
                              || d.getPickupLocation().toLowerCase().contains(search)
                              || (d.getDietaryType() != null && d.getDietaryType().toLowerCase().contains(search)))
                    .collect(Collectors.toList());
        }

        model.addAttribute("user", ngo);
        model.addAttribute("keyword", keyword);
        model.addAttribute("availableDonations", available);
        model.addAttribute("myRequests", donationRepository.findByNgo(ngo));
        return "ngo-dashboard";
    }

    @PostMapping("/ngo/request/{id}")
    public String requestDonation(@PathVariable Long id, Authentication auth, RedirectAttributes redirectAttributes) {
        lockExpiredDonations();
        FoodDonation donation = donationRepository.findById(id).orElseThrow();
        if (!donation.getStatus().equals("AVAILABLE")) {
            redirectAttributes.addFlashAttribute("errorMsg", "Sorry, this donation is no longer available or has expired.");
            return "redirect:/ngo/dashboard";
        }
        donation.setNgo(getLoggedInUser(auth));
        donation.setStatus("REQUESTED");
        donationRepository.save(donation);
        return "redirect:/ngo/dashboard";
    }

    // --- ADMIN WORKFLOW, CSV EXPORT & IMPACT STATS ---

@GetMapping("/admin/dashboard")
public String adminDashboard(Model model) {
    lockExpiredDonations();

    model.addAttribute("allDonations", donationRepository.findAll());
    model.addAttribute("volunteers", userRepository.findByRole("ROLE_VOLUNTEER"));
    model.addAttribute("leaderboard", getVolunteerLeaderboard());
    model.addAttribute("allUsers", userRepository.findAll());

    model.addAllAttributes(calculateImpactMetrics());

    return "admin-dashboard";
}


    @PostMapping("/admin/assign/{donationId}")
    public String assignVolunteer(@PathVariable Long donationId, @RequestParam Long volunteerId) {
        FoodDonation donation = donationRepository.findById(donationId).orElseThrow();
        User volunteer = userRepository.findById(volunteerId).orElseThrow();

        donation.setVolunteer(volunteer);
        donation.setStatus("ALLOCATED");
        donation.setPickupOtp(generateOtp());
        donation.setDeliveryOtp(generateOtp());

        donationRepository.save(donation);
        return "redirect:/admin/dashboard";
    }

    @GetMapping("/admin/export/csv")
    public void exportDonationsCsv(HttpServletResponse response) throws IOException {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"food_rescue_report.csv\"");

        PrintWriter writer = response.getWriter();
        writer.println("Donation ID,Donor Name,Food Type,Dietary Tag,Allergens,Quantity,Pickup Location,NGO Name,Volunteer Name,Status");

        for (FoodDonation d : donationRepository.findAll()) {
            String donorName = d.getDonor() != null ? d.getDonor().getName().replace(",", " ") : "-";
            String ngoName = d.getNgo() != null ? d.getNgo().getName().replace(",", " ") : "-";
            String volName = d.getVolunteer() != null ? d.getVolunteer().getName().replace(",", " ") : "-";
            String location = d.getPickupLocation() != null ? d.getPickupLocation().replace(",", " ") : "-";
            String diet = d.getDietaryType() != null ? d.getDietaryType().replace(",", " ") : "Standard";
            String allergen = d.getAllergens() != null ? d.getAllergens().replace(",", " ") : "None";

            writer.printf("%d,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                    d.getId(), donorName, d.getFoodType(), diet, allergen,
                    d.getQuantity(), location, ngoName, volName, d.getStatus());
        }
        writer.flush();
    }

    // --- VOLUNTEER WORKFLOW (Self-Claim Pool, Route Optimization, OTP & Gamification Points) ---
    @GetMapping("/volunteer/dashboard")
    public String volunteerDashboard(Model model, Authentication auth) {
        lockExpiredDonations();
        User volunteer = getLoggedInUser(auth);
        List<FoodDonation> allTasks = donationRepository.findByVolunteer(volunteer);

        List<FoodDonation> optimizedRoute = allTasks.stream()
                .filter(t -> !t.getStatus().equals("DELIVERED") && !t.getStatus().equals("EXPIRED"))
                .sorted(Comparator
                        .comparing((FoodDonation d) -> d.getStatus().equals("PICKED_UP") ? 0 : 1)
                        .thenComparing(FoodDonation::getAvailableUntil, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(FoodDonation::getPickupLocation, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        List<String> waypoints = new ArrayList<>();
        if (volunteer.getAddress() != null && !volunteer.getAddress().isBlank()) {
            waypoints.add(URLEncoder.encode(volunteer.getAddress(), StandardCharsets.UTF_8));
        }

        for (int i = 0; i < optimizedRoute.size(); i++) {
            FoodDonation task = optimizedRoute.get(i);
            task.setRouteSequence(i + 1);

            String nextStopAddress = task.getStatus().equals("ALLOCATED")
                    ? task.getPickupLocation()
                    : task.getNgo().getAddress();

            if (nextStopAddress != null && !nextStopAddress.isBlank()) {
                waypoints.add(URLEncoder.encode(nextStopAddress, StandardCharsets.UTF_8));
            }
        }

        String fullRouteUrl = waypoints.size() >= 2
                ? "https://google.com" + "/maps/dir/" + String.join("/", waypoints)
                : null;

        List<FoodDonation> openPickups = donationRepository.findByStatus("APPROVED");

        List<FoodDonation> completedTasks = allTasks.stream()
                .filter(t -> t.getStatus().equals("DELIVERED"))
                .collect(Collectors.toList());

        model.addAttribute("user", volunteer);
        model.addAttribute("optimizedRoute", optimizedRoute);
        model.addAttribute("fullRouteUrl", fullRouteUrl);
        model.addAttribute("openPickups", openPickups);
        model.addAttribute("completedTasks", completedTasks);
        model.addAttribute("leaderboard", getVolunteerLeaderboard());
        return "volunteer-dashboard";
    }

    @PostMapping("/volunteer/claim/{id}")
    public String claimPickup(@PathVariable Long id, Authentication auth, RedirectAttributes redirectAttributes) {
        FoodDonation donation = donationRepository.findById(id).orElseThrow();
        if (donation.getStatus().equals("APPROVED") && donation.getVolunteer() == null) {
            donation.setVolunteer(getLoggedInUser(auth));
            donation.setStatus("ALLOCATED");
            donation.setPickupOtp(generateOtp());
            donation.setDeliveryOtp(generateOtp());
            donationRepository.save(donation);
            redirectAttributes.addFlashAttribute("successMsg", "Order claimed! Pickup & Delivery OTPs generated.");
        } else {
            redirectAttributes.addFlashAttribute("errorMsg", "Order was already claimed by another volunteer.");
        }
        return "redirect:/volunteer/dashboard";
    }

    @PostMapping("/volunteer/verify-pickup/{id}")
    public String verifyPickup(@PathVariable Long id, @RequestParam String otp, RedirectAttributes redirectAttributes) {
        FoodDonation donation = donationRepository.findById(id).orElseThrow();
        if (donation.getPickupOtp() != null && donation.getPickupOtp().equals(otp.trim())) {
            donation.setStatus("PICKED_UP");
            donationRepository.save(donation);
            redirectAttributes.addFlashAttribute("successMsg", "Pickup OTP verified! Food collected.");
        } else {
            redirectAttributes.addFlashAttribute("errorMsg", "Invalid Pickup OTP! Please ask the Donor for the 4-digit code.");
        }
        return "redirect:/volunteer/dashboard";
    }

    @PostMapping("/volunteer/verify-delivery/{id}")
    public String verifyDelivery(@PathVariable Long id, @RequestParam String otp, RedirectAttributes redirectAttributes) {
        FoodDonation donation = donationRepository.findById(id).orElseThrow();
        if (donation.getDeliveryOtp() != null && donation.getDeliveryOtp().equals(otp.trim())) {
            donation.setStatus("DELIVERED");
            donationRepository.save(donation);

            User volunteer = donation.getVolunteer();
            if (volunteer != null) {
                int earned = 50;
                if (donation.getAvailableUntil() != null && LocalDateTime.now().isBefore(donation.getAvailableUntil())) {
                    earned += 20;
                }
                volunteer.setPoints(volunteer.getPoints() + earned);
                userRepository.save(volunteer);
                redirectAttributes.addFlashAttribute("successMsg", "Delivery OTP verified! You earned +" + earned + " Rescue Points!");
            } else {
                redirectAttributes.addFlashAttribute("successMsg", "Delivery OTP verified! Order completed.");
            }
        } else {
            redirectAttributes.addFlashAttribute("errorMsg", "Invalid Delivery OTP! Please ask the NGO for the 4-digit code.");
        }
        return "redirect:/volunteer/dashboard";
    }
}
