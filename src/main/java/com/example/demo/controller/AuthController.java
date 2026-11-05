package com.example.demo.controller;

import com.example.demo.model.Artisan;
import com.example.demo.model.Customer;
import com.example.demo.model.User;
import com.example.demo.repository.ArtisanRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.example.demo.repository.CustomerRepository customerRepository;

    @Autowired
    private ArtisanRepository artisanRepository;

    @Autowired
    private com.example.demo.repository.WishlistRepository wishlistRepository;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        String password = payload.get("password");
        if (email != null) {
            email = email.trim().toLowerCase();
        }

        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            boolean passwordMatch = false;
            String storedPassword = user.getPassword();

            if (storedPassword != null) {
                if (storedPassword.startsWith("$2a$") || storedPassword.startsWith("$2b$")
                        || storedPassword.startsWith("$2y$")) {
                    try {
                        passwordMatch = BCrypt.checkpw(password, storedPassword);
                    } catch (Exception e) {
                        passwordMatch = false;
                    }
                } else {
                    passwordMatch = password.equals(storedPassword);
                }
            }

            if (passwordMatch) {
                if ("suspended".equalsIgnoreCase(user.getStatus())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("success", false, "message",
                                    "Your account has been suspended because you violated our Terms of Service and Policies."));
                }
                if ("pending approval".equalsIgnoreCase(user.getStatus())
                        || "pending".equalsIgnoreCase(user.getStatus())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("success", false, "message",
                                    "Your artisan account is pending approval. Please wait for an administrator to review your application."));
                }
                if ("rejected".equalsIgnoreCase(user.getStatus())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("success", false, "message",
                                    "Your artisan registration request has been rejected."));
                }
                user.setLastActive(java.time.LocalDateTime.now());
                userRepository.save(user);

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                if (user instanceof Customer) {
                    customerRepository.findByEmail(email)
                            .ifPresentOrElse(c -> response.put("user", c), () -> response.put("user", user));
                } else if ("ARTISAN".equalsIgnoreCase(user.getRole())) {
                    artisanRepository.findByEmail(email)
                            .ifPresentOrElse(a -> response.put("user", a), () -> response.put("user", user));
                } else {
                    response.put("user", user);
                }
                return ResponseEntity.ok(response);
            }
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("success", false, "message", "Invalid email or password"));
    }

    @PostMapping("/register/customer")
    public ResponseEntity<?> registerCustomer(@RequestBody Customer customer) {
        if (customer.getEmail() != null) {
            customer.setEmail(customer.getEmail().trim().toLowerCase());
        }
        if (userRepository.findByEmailIgnoreCase(customer.getEmail()).isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "message", "Email already exists"));
        }
        if (userRepository.findByNameIgnoreCase(customer.getName()).isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "message", "Name already exists"));
        }
        if (customer.getPhone() == null || !customer.getPhone().replaceAll("\\s+", "").matches("\\+?[0-9]{7,15}")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "message", "Please enter a valid phone number."));
        }
        customer.setRole("CUSTOMER");
        customer.setPassword(BCrypt.hashpw(customer.getPassword(), BCrypt.gensalt()));
        customer.setPhone(customer.getPhone().replaceAll("\\s+", ""));
        customer.setTotalOrders(0);
        customer.setTotalSpent(0.0);
        customer.setStatus("active");
        userRepository.save(customer);

        com.example.demo.model.Wishlist wishlist = new com.example.demo.model.Wishlist();
        wishlist.setCustomer(customer);
        wishlist.setDateCreated(java.time.LocalDate.now());
        wishlistRepository.save(wishlist);

        return ResponseEntity.ok(Map.of("success", true, "user", customer));
    }

    @PostMapping("/register/artisan")
    public ResponseEntity<?> registerArtisan(@RequestBody Artisan artisan) {
        if (artisan.getEmail() != null) {
            artisan.setEmail(artisan.getEmail().trim().toLowerCase());
        }
        if (userRepository.findByEmailIgnoreCase(artisan.getEmail()).isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "message", "Email already exists"));
        }
        if (userRepository.findByNameIgnoreCase(artisan.getName()).isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "message", "Name already exists"));
        }
        if (artisan.getPhone() == null || !artisan.getPhone().replaceAll("\\s+", "").matches("\\+?[0-9]{7,15}")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "message", "Please enter a valid phone number."));
        }
        artisan.setRole("ARTISAN");
        artisan.setPassword(BCrypt.hashpw(artisan.getPassword(), BCrypt.gensalt()));
        artisan.setPhone(artisan.getPhone().replaceAll("\\s+", ""));
        if (artisan.getProfilePicture() == null || artisan.getProfilePicture().isBlank()) {
            artisan.setProfilePicture("https://cdn-icons-png.flaticon.com/512/149/149071.png");
        }
        artisan.setStatus("pending");

        Artisan saved = artisanRepository.save(artisan);
        return ResponseEntity.ok(Map.of("success", true, "user", saved));
    }

    @DeleteMapping("/delete-account")
    @Transactional
    public ResponseEntity<?> deleteAccount(@RequestParam String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (user instanceof Customer) {
                Optional<com.example.demo.model.Wishlist> wishlistOpt = wishlistRepository
                        .findByCustomer((Customer) user);
                wishlistOpt.ifPresent(wishlist -> wishlistRepository.delete(wishlist));
            }
            // Add similar logic for Artisan products if needed, or just let JPA handle it
            // if configured
            userRepository.delete(user);
            return ResponseEntity.ok(Map.of("success", true, "message", "Account deleted successfully"));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", "User not found"));
    }

    @PostMapping("/logout")
    @Transactional
    public ResponseEntity<?> logout(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        if (email != null) {
            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                user.setLastActive(java.time.LocalDateTime.now());
                userRepository.save(user);
                return ResponseEntity.ok(Map.of("success", true));
            }
        }
        return ResponseEntity.ok(Map.of("success", true)); // Still return success to allow frontend logout to proceed
    }

    @PostMapping("/change-password")
    @Transactional
    public ResponseEntity<?> changePassword(@RequestBody java.util.Map<String, Object> payload) {
        String email = (String) payload.get("email");
        String oldPassword = (String) payload.get("oldPassword");
        String newPassword = (String) payload.get("newPassword");
        if (email == null || oldPassword == null || newPassword == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Missing required fields"));
        }
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "User not found"));
        }
        User user = userOpt.get();
        String stored = user.getPassword();
        boolean match = false;
        if (stored != null && (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$"))) {
            try {
                match = BCrypt.checkpw(oldPassword, stored);
            } catch (Exception e) {
                match = false;
            }
        } else {
            match = oldPassword.equals(stored);
        }
        if (!match) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Current password is incorrect"));
        }
        if (newPassword.length() < 6) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "New password must be at least 6 characters"));
        }
        user.setPassword(BCrypt.hashpw(newPassword, BCrypt.gensalt()));
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("success", true, "message", "Password updated"));
    }
}
