package com.project.foodwaste.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "food_donations")
public class FoodDonation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String foodType;

    @Column(nullable = false)
    private String quantity;

    private LocalDateTime preparationTime;
    private LocalDateTime availableUntil;

    @Column(nullable = false)
    private String pickupLocation;

    // Status: AVAILABLE, REQUESTED, ALLOCATED, PICKED_UP, DELIVERED
    @Column(nullable = false)
    private String status = "AVAILABLE";

    @ManyToOne
    @JoinColumn(name = "donor_id", nullable = false)
    private User donor;

    @ManyToOne
    @JoinColumn(name = "ngo_id")
    private User ngo;

    @ManyToOne
    @JoinColumn(name = "volunteer_id")
    private User volunteer;
    
    
 // Add these fields inside FoodDonation.java
    private String pickupOtp;
    private String deliveryOtp;
    private Integer routeSequence;

 // Add these fields inside FoodDonation.java
    private String dietaryType; // e.g., Pure Veg, Non-Veg, Jain / No Onion-Garlic
    private String allergens;   // e.g., None, Dairy, Nuts, Gluten
    
    // Default Constructor
    public FoodDonation() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFoodType() { return foodType; }
    public void setFoodType(String foodType) { this.foodType = foodType; }

    public String getQuantity() { return quantity; }
    public void setQuantity(String quantity) { this.quantity = quantity; }

    public LocalDateTime getPreparationTime() { return preparationTime; }
    public void setPreparationTime(LocalDateTime preparationTime) { this.preparationTime = preparationTime; }

    public LocalDateTime getAvailableUntil() { return availableUntil; }
    public void setAvailableUntil(LocalDateTime availableUntil) { this.availableUntil = availableUntil; }

    public String getPickupLocation() { return pickupLocation; }
    public void setPickupLocation(String pickupLocation) { this.pickupLocation = pickupLocation; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public User getDonor() { return donor; }
    public void setDonor(User donor) { this.donor = donor; }

    public User getNgo() { return ngo; }
    public void setNgo(User ngo) { this.ngo = ngo; }

    public User getVolunteer() { return volunteer; }
    public void setVolunteer(User volunteer) { this.volunteer = volunteer; }
    
    

    // Add these Getters and Setters at the bottom of FoodDonation.java
    public String getPickupOtp() { return pickupOtp; }
    public void setPickupOtp(String pickupOtp) { this.pickupOtp = pickupOtp; }

    public String getDeliveryOtp() { return deliveryOtp; }
    public void setDeliveryOtp(String deliveryOtp) { this.deliveryOtp = deliveryOtp; }

    public Integer getRouteSequence() { return routeSequence; }
    public void setRouteSequence(Integer routeSequence) { this.routeSequence = routeSequence; }
    
    public String getDietaryType() { return dietaryType; }
    public void setDietaryType(String dietaryType) { this.dietaryType = dietaryType; }

    public String getAllergens() { return allergens; }
    public void setAllergens(String allergens) { this.allergens = allergens; }

}
