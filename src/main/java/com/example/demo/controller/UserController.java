package com.example.demo.controller;

import com.example.demo.model.Artisan;
import com.example.demo.model.Customer;
import com.example.demo.model.User;
import com.example.demo.repository.ArtisanRepository;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ArtisanRepository artisanRepository;

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@RequestParam String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "User not found"));
        }
        User user = userOpt.get();
        if (user instanceof Customer) {
            return customerRepository.findByEmail(email)
                    .map(customer -> ResponseEntity.ok(Map.of("success", true, "user", customer)))
                    .orElse(ResponseEntity.status(404).body(Map.of("success", false, "message", "User not found")));
        }
        if (user instanceof Artisan || "ARTISAN".equalsIgnoreCase(user.getRole())) {
            return artisanRepository.findByEmail(email)
                    .map(artisan -> ResponseEntity.ok(Map.of("success", true, "user", artisan)))
                    .orElse(ResponseEntity.status(404).body(Map.of("success", false, "message", "User not found")));
        }
        return ResponseEntity.ok(Map.of("success", true, "user", user));
    }

    @PostMapping("/update-profile-picture")
    public ResponseEntity<?> updateProfilePicture(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        String imageUrl = payload.get("imageUrl");

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (("ARTISAN".equalsIgnoreCase(user.getRole()) || user instanceof Artisan) && !"active".equalsIgnoreCase(user.getStatus())) {
                return ResponseEntity.status(403).body(Map.of("success", false, "message", "Your artisan account is not active. Access denied."));
            }
            user.setProfilePicture(imageUrl);
            userRepository.save(user);
            if (user instanceof Customer) {
                return customerRepository.findByEmail(email)
                        .map(customer -> ResponseEntity.ok(Map.of("success", true, "user", customer)))
                        .orElse(ResponseEntity.ok(Map.of("success", true, "user", user)));
            }
            if (user instanceof Artisan || "ARTISAN".equalsIgnoreCase(user.getRole())) {
                return artisanRepository.findByEmail(email)
                        .map(artisan -> ResponseEntity.ok(Map.of("success", true, "user", artisan)))
                        .orElse(ResponseEntity.ok(Map.of("success", true, "user", user)));
            }
            return ResponseEntity.ok(Map.of("success", true, "user", user));
        }
        return ResponseEntity.status(404).body(Map.of("success", false, "message", "User not found"));
    }

    @PostMapping("/update-artisan-settings")
    public ResponseEntity<?> updateArtisanSettings(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        String name = payload.get("name");
        String phone = payload.get("phone");
        String shopName = payload.get("shopName");
        String biography = payload.get("biography");

        Optional<Artisan> artisanOpt = artisanRepository.findByEmail(email);
        if (artisanOpt.isPresent()) {
            Artisan artisan = artisanOpt.get();
            if (!"active".equalsIgnoreCase(artisan.getStatus())) {
                return ResponseEntity.status(403).body(Map.of("success", false, "message", "Your artisan account is not active. Access denied."));
            }
            if (name != null) artisan.setName(name);
            if (phone != null) artisan.setPhone(phone);
            if (shopName != null) artisan.setShopName(shopName);
            if (biography != null) artisan.setBiography(biography);
            artisanRepository.save(artisan);
            return ResponseEntity.ok(Map.of("success", true, "user", artisan));
        }
        return ResponseEntity.status(404).body(Map.of("success", false, "message", "Artisan not found"));
    }

    @PostMapping("/update-profile")
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        String name = payload.get("name");
        String phone = payload.get("phone");
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (!"active".equalsIgnoreCase(user.getStatus())) {
                return ResponseEntity.status(403).body(Map.of("success", false, "message", "Account is not active."));
            }
            if (name != null) user.setName(name);
            if (phone != null) user.setPhone(phone);
            // Email change handling: ensure uniqueness
            if (email != null && !email.equals(user.getEmail())) {
                if (userRepository.findByEmail(email).isPresent()) {
                    return ResponseEntity.status(400).body(Map.of("success", false, "message", "Email already in use."));
                }
                user.setEmail(email);
            }
            userRepository.save(user);
            // Return updated user (including role-specific data)
            if (user instanceof Customer) {
                return customerRepository.findByEmail(user.getEmail())
                        .map(c -> ResponseEntity.ok(Map.of("success", true, "user", c)))
                        .orElse(ResponseEntity.ok(Map.of("success", true, "user", user)));
            }
            if (user instanceof Artisan || "ARTISAN".equalsIgnoreCase(user.getRole())) {
                return artisanRepository.findByEmail(user.getEmail())
                        .map(a -> ResponseEntity.ok(Map.of("success", true, "user", a)))
                        .orElse(ResponseEntity.ok(Map.of("success", true, "user", user)));
            }
            return ResponseEntity.ok(Map.of("success", true, "user", user));
        }
        return ResponseEntity.status(404).body(Map.of("success", false, "message", "User not found"));
    }

@PostMapping("/update-shop-banner")
public ResponseEntity<?> updateShopBanner(@RequestBody Map<String, String> payload) {
    String email = payload.get("email");
    String bannerUrl = payload.get("bannerUrl");
    Optional<Artisan> artisanOpt = artisanRepository.findByEmail(email);
    if (artisanOpt.isPresent()) {
        Artisan artisan = artisanOpt.get();
        if (!"active".equalsIgnoreCase(artisan.getStatus())) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "Your artisan account is not active. Access denied."));
        }
        artisan.setShopBanner(bannerUrl);
        artisanRepository.save(artisan);
        return ResponseEntity.ok(Map.of("success", true, "user", artisan));
    }
    return ResponseEntity.status(404).body(Map.of("success", false, "message", "Artisan not found"));
}
}
