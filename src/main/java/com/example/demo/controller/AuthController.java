package com.example.demo.controller;

import com.example.demo.dto.AuthRequests;
import com.example.demo.model.Artisan;
import com.example.demo.model.Customer;
import com.example.demo.model.User;
import com.example.demo.security.CurrentUser;
import com.example.demo.service.AccountService;
import com.example.demo.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AccountService accountService;
    private final CurrentUser currentUser;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AuthRequests.Login body,
            HttpServletRequest request, HttpServletResponse response) {
        User user = authService.authenticate(body.email(), body.password());
        authService.startSession(user, request, response);
        return ResponseEntity.ok(Map.of("success", true, "user", user));
    }

    /** Current session's user, or 401. Lets the front end detect an expired session. */
    @GetMapping("/session")
    public ResponseEntity<?> session() {
        User user = currentUser.require();
        return ResponseEntity.ok(Map.of("success", true, "user", user));
    }

    @PostMapping("/register/customer")
    public ResponseEntity<?> registerCustomer(@Valid @RequestBody AuthRequests.Register body,
            HttpServletRequest request, HttpServletResponse response) {
        Customer customer = authService.registerCustomer(body);
        // The front end treats a new customer as signed in straight away.
        authService.startSession(customer, request, response);
        return ResponseEntity.ok(Map.of("success", true, "user", customer));
    }

    @PostMapping("/register/artisan")
    public ResponseEntity<?> registerArtisan(@Valid @RequestBody AuthRequests.Register body) {
        // Artisans start as "pending" and cannot sign in until an admin approves them.
        Artisan saved = authService.registerArtisan(body);
        return ResponseEntity.ok(Map.of("success", true, "user", saved));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        currentUser.id().ifPresent(authService::touchLastActive);
        authService.endSession(request);
        return ResponseEntity.ok(Map.of("success", true));
    }

    /** Records "last active" when the user leaves a page. Does not end the session. */
    @PostMapping("/activity")
    public ResponseEntity<?> activity() {
        currentUser.id().ifPresent(authService::touchLastActive);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody AuthRequests.ChangePassword body) {
        authService.changePassword(currentUser.require(), body.oldPassword(), body.newPassword());
        return ResponseEntity.ok(Map.of("success", true, "message", "Password updated"));
    }

    @DeleteMapping("/delete-account")
    public ResponseEntity<?> deleteAccount(@Valid @RequestBody AuthRequests.DeleteAccount body,
            HttpServletRequest request) {
        User user = currentUser.require();
        if (!authService.passwordMatches(user, body.password())) {
            // 400, not 401: the front end treats 401 as "session expired".
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is incorrect.");
        }
        accountService.closeAccount(user);
        authService.endSession(request);
        return ResponseEntity.ok(Map.of("success", true, "message", "Account deleted successfully"));
    }
}
