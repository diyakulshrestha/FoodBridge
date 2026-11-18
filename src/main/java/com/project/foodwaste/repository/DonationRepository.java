package com.project.foodwaste.repository;

import com.project.foodwaste.model.FoodDonation;
import com.project.foodwaste.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DonationRepository extends JpaRepository<FoodDonation, Long> {
    List<FoodDonation> findByStatus(String status);
    List<FoodDonation> findByDonor(User donor);
    List<FoodDonation> findByNgo(User ngo);
    List<FoodDonation> findByVolunteer(User volunteer);
    List<FoodDonation> findByStatusAndPickupLocationContainingIgnoreCaseOrStatusAndFoodTypeContainingIgnoreCase(
            String status1, String location, String status2, String foodType);
}
