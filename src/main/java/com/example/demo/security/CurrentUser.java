package com.example.demo.security;

import com.example.demo.model.Artisan;
import com.example.demo.model.Customer;
import com.example.demo.model.Roles;
import com.example.demo.model.User;
import com.example.demo.repository.ArtisanRepository;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/**
 * The only supported way for controllers and services to find out who is calling.
 * Reads the principal from the session and loads the current row from the database.
 */
@Component
@RequiredArgsConstructor
public class CurrentUser {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final ArtisanRepository artisanRepository;

    public Optional<AuthenticatedUser> principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    public Optional<Long> id() {
        return principal().map(AuthenticatedUser::userId);
    }

    public Optional<User> find() {
        return id().flatMap(userRepository::findById);
    }

    /** @throws ResponseStatusException 401 when nobody is signed in. */
    public User require() {
        return find().orElseThrow(CurrentUser::notSignedIn);
    }

    public boolean hasRole(String role) {
        return principal().map(p -> role.equalsIgnoreCase(p.role())).orElse(false);
    }

    /** @throws ResponseStatusException 401 when signed out, 403 when the caller is not a customer. */
    public Customer requireCustomer() {
        Long id = id().orElseThrow(CurrentUser::notSignedIn);
        return customerRepository.findById(id).orElseThrow(() -> forbidden("Only customer accounts can do this."));
    }

    /** @throws ResponseStatusException 401 when signed out, 403 when the caller is not an artisan. */
    public Artisan requireArtisan() {
        Long id = id().orElseThrow(CurrentUser::notSignedIn);
        return artisanRepository.findById(id).orElseThrow(() -> forbidden("Only artisan accounts can do this."));
    }

    /** @throws ResponseStatusException 401 when signed out, 403 when the caller is not an admin. */
    public User requireAdmin() {
        User user = require();
        if (!Roles.ADMIN.equalsIgnoreCase(user.getRole())) {
            throw forbidden("Administrator access required.");
        }
        return user;
    }

    public static ResponseStatusException notSignedIn() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in first.");
    }

    public static ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }
}
