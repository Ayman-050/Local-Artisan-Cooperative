package com.example.demo.service;

import com.example.demo.dto.AuthRequests;
import com.example.demo.model.Artisan;
import com.example.demo.model.Customer;
import com.example.demo.model.Roles;
import com.example.demo.model.User;
import com.example.demo.model.UserStatus;
import com.example.demo.model.Wishlist;
import com.example.demo.repository.ArtisanRepository;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.WishlistRepository;
import com.example.demo.security.AuthenticatedUser;
import com.example.demo.web.Phones;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final ArtisanRepository artisanRepository;
    private final WishlistRepository wishlistRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityContextRepository securityContextRepository;

    /**
     * Pre-computed hash compared against when the e-mail is unknown, so a failed login takes the same
     * time whether or not the account exists (prevents probing for registered e-mails by timing).
     */
    private String dummyHash;

    private static final String INVALID_CREDENTIALS = "Invalid email or password";

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Checks credentials and account status.
     *
     * @throws ResponseStatusException 401 for bad credentials, 403 for accounts that may not log in
     */
    @Transactional
    public User authenticate(String email, String rawPassword) {
        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(normalizeEmail(email));
        if (userOpt.isEmpty()) {
            passwordEncoder.matches(rawPassword, dummyHash());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS);
        }
        User user = userOpt.get();
        if (!passwordMatches(user, rawPassword)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS);
        }
        String status = user.getStatus();
        if (UserStatus.SUSPENDED.equalsIgnoreCase(status)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Your account has been suspended because you violated our Terms of Service and Policies.");
        }
        if (UserStatus.isPending(status)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Your artisan account is pending approval. Please wait for an administrator to review your application.");
        }
        if (UserStatus.REJECTED.equalsIgnoreCase(status)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Your artisan registration request has been rejected.");
        }
        if (!UserStatus.isActive(status)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS);
        }
        user.setLastActive(LocalDateTime.now());
        return user;
    }

    /** Only BCrypt hashes are accepted; legacy plain-text rows are upgraded at startup. */
    public boolean passwordMatches(User user, String rawPassword) {
        String stored = user.getPassword();
        if (rawPassword == null || stored == null || !stored.startsWith("$2")) {
            return false;
        }
        try {
            return passwordEncoder.matches(rawPassword, stored);
        } catch (IllegalArgumentException malformedHash) {
            return false;
        }
    }

    /** Stores the user in a fresh HTTP session (a new session id prevents session fixation). */
    public void startSession(User user, HttpServletRequest request, HttpServletResponse response) {
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            existing.invalidate();
        }
        request.getSession(true);

        String role = user.getRole() != null ? user.getRole().toUpperCase(Locale.ROOT) : Roles.CUSTOMER;
        var principal = new AuthenticatedUser(user.getUserId(), role);
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    public void endSession(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    @Transactional
    public Customer registerCustomer(AuthRequests.Register req) {
        String email = assertAvailable(req);
        Customer customer = new Customer();
        customer.setName(req.name().trim());
        customer.setEmail(email);
        customer.setPhone(normalizePhone(req.phone()));
        customer.setPassword(passwordEncoder.encode(req.password()));
        customer.setRole(Roles.CUSTOMER);
        customer.setStatus(UserStatus.ACTIVE);
        Customer saved = customerRepository.save(customer);

        Wishlist wishlist = new Wishlist();
        wishlist.setCustomer(saved);
        wishlist.setDateCreated(LocalDate.now());
        wishlistRepository.save(wishlist);
        return saved;
    }

    @Transactional
    public Artisan registerArtisan(AuthRequests.Register req) {
        String email = assertAvailable(req);
        Artisan artisan = new Artisan();
        artisan.setName(req.name().trim());
        artisan.setEmail(email);
        artisan.setPhone(normalizePhone(req.phone()));
        artisan.setPassword(passwordEncoder.encode(req.password()));
        artisan.setRole(Roles.ARTISAN);
        artisan.setStatus(UserStatus.PENDING);
        artisan.setShopName(req.shopName());
        artisan.setBiography(req.biography());
        if (req.profilePicture() != null && !req.profilePicture().isBlank()) {
            artisan.setProfilePicture(req.profilePicture());
        }
        return artisanRepository.save(artisan);
    }

    @Transactional
    public void changePassword(User user, String oldPassword, String newPassword) {
        if (!passwordMatches(user, oldPassword)) {
            // 400, not 401: the front end treats 401 as "session expired".
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public void touchLastActive(Long userId) {
        userRepository.findById(userId).ifPresent(u -> u.setLastActive(LocalDateTime.now()));
    }

    private String assertAvailable(AuthRequests.Register req) {
        String email = normalizeEmail(req.email());
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new IllegalArgumentException("Email already exists");
        }
        if (userRepository.findByNameIgnoreCase(req.name().trim()).isPresent()) {
            throw new IllegalArgumentException("Name already exists");
        }
        return email;
    }

    private static String normalizePhone(String phone) {
        return Phones.normalize(phone);
    }

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode("timing-equalisation-only");
        }
        return dummyHash;
    }
}
