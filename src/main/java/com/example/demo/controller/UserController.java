package com.example.demo.controller;

import com.example.demo.model.Artisan;
import com.example.demo.model.User;
import com.example.demo.repository.AddressRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.CurrentUser;
import com.example.demo.service.AuthService;
import com.example.demo.web.ImageUrls;
import com.example.demo.web.Phones;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Profile endpoints for the signed-in user. The user is always taken from the session; an {@code email}
 * field in the body is only ever treated as the <em>new</em> e-mail on /update-profile.
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private static final int MAX_NAME = 100;
    private static final int MAX_TEXT = 255;

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final CurrentUser currentUser;

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        return ok(currentUser.require());
    }

    @PostMapping("/update-profile-picture")
    @Transactional
    public ResponseEntity<?> updateProfilePicture(@RequestBody Map<String, String> payload) {
        User user = currentUser.require();
        String image = ImageUrls.validate(payload.get("imageUrl"), "Profile picture");
        user.setProfilePicture(image != null ? image : User.DEFAULT_PROFILE_PICTURE);
        return ok(userRepository.save(user));
    }

    @PostMapping("/update-artisan-settings")
    @Transactional
    public ResponseEntity<?> updateArtisanSettings(@RequestBody Map<String, String> payload) {
        Artisan artisan = currentUser.requireArtisan();
        String name = payload.get("name");
        if (name != null) {
            artisan.setName(validName(name, artisan));
        }
        if (payload.get("phone") != null) {
            artisan.setPhone(validPhone(payload.get("phone")));
        }
        if (payload.get("shopName") != null) {
            artisan.setShopName(limit(payload.get("shopName"), MAX_TEXT, "Shop name"));
        }
        if (payload.get("biography") != null) {
            artisan.setBiography(limit(payload.get("biography"), MAX_TEXT, "Biography"));
        }
        return ok(userRepository.save(artisan));
    }

    @PostMapping("/update-profile")
    @Transactional
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, String> payload) {
        User user = currentUser.require();
        if (payload.get("name") != null) {
            user.setName(validName(payload.get("name"), user));
        }
        if (payload.get("phone") != null) {
            user.setPhone(validPhone(payload.get("phone")));
        }
        String newEmail = AuthService.normalizeEmail(payload.get("email"));
        if (newEmail != null && !newEmail.isBlank() && !newEmail.equalsIgnoreCase(user.getEmail())) {
            if (!newEmail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") || newEmail.length() > MAX_TEXT) {
                throw new IllegalArgumentException("Please enter a valid email address.");
            }
            if (userRepository.findByEmailIgnoreCase(newEmail).isPresent()) {
                throw new IllegalArgumentException("Email already in use.");
            }
            // Saved addresses are keyed by e-mail; move them along with the account.
            addressRepository.reassignEmail(user.getEmail(), newEmail);
            user.setEmail(newEmail);
        }
        return ok(userRepository.save(user));
    }

    @PostMapping("/update-shop-banner")
    @Transactional
    public ResponseEntity<?> updateShopBanner(@RequestBody Map<String, String> payload) {
        Artisan artisan = currentUser.requireArtisan();
        artisan.setShopBanner(ImageUrls.validate(payload.get("bannerUrl"), "Shop banner"));
        return ok(userRepository.save(artisan));
    }

    private String validName(String raw, User self) {
        String name = raw.trim();
        if (name.isEmpty() || name.length() > MAX_NAME) {
            throw new IllegalArgumentException("Name must be between 1 and " + MAX_NAME + " characters.");
        }
        userRepository.findByNameIgnoreCase(name)
                .filter(other -> !other.getUserId().equals(self.getUserId()))
                .ifPresent(other -> {
                    throw new IllegalArgumentException("Name already exists");
                });
        return name;
    }

    private static String validPhone(String raw) {
        return Phones.normalize(raw);
    }

    private static String limit(String value, int max, String label) {
        String v = value.trim();
        if (v.length() > max) {
            throw new IllegalArgumentException(label + " is too long (" + max + " characters max).");
        }
        return v;
    }

    private static ResponseEntity<?> ok(User user) {
        return ResponseEntity.ok(Map.of("success", true, "user", user));
    }
}
