package com.example.demo.controller;

import com.example.demo.model.Customer;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.CartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    @Autowired
    private CartService cartService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @GetMapping
    public ResponseEntity<?> getCart(@RequestHeader(value = "X-User-Email", required = false) String email) {
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Missing X-User-Email"));
        }
        Optional<ResponseEntity<?>> denied = verifyCustomer(email);
        if (denied.isPresent()) {
            return denied.get();
        }
        try {
            return ResponseEntity.ok(cartService.getCartPayload(email.trim()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    @PostMapping("/add")
    public ResponseEntity<?> add(@RequestHeader(value = "X-User-Email", required = false) String email,
                                 @RequestBody Map<String, Object> body) {
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Missing X-User-Email"));
        }
        Optional<ResponseEntity<?>> denied = verifyCustomer(email);
        if (denied.isPresent()) {
            return denied.get();
        }
        Object pid = body != null ? body.get("productId") : null;
        if (pid == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "productId required"));
        }
        long productId = pid instanceof Number ? ((Number) pid).longValue() : Long.parseLong(String.valueOf(pid));
        int qty = 1;
        if (body != null && body.get("quantity") != null) {
            Object q = body.get("quantity");
            qty = q instanceof Number ? ((Number) q).intValue() : Integer.parseInt(String.valueOf(q));
        }
        try {
            return ResponseEntity.ok(cartService.addItem(email.trim(), productId, qty));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    @PatchMapping("/items/{orderItemId}")
    public ResponseEntity<?> updateQuantity(@RequestHeader(value = "X-User-Email", required = false) String email,
                                            @PathVariable Long orderItemId,
                                            @RequestBody Map<String, Object> body) {
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Missing X-User-Email"));
        }
        Optional<ResponseEntity<?>> denied = verifyCustomer(email);
        if (denied.isPresent()) {
            return denied.get();
        }
        if (body == null || body.get("quantity") == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "quantity required"));
        }
        Object q = body.get("quantity");
        int qty = q instanceof Number ? ((Number) q).intValue() : Integer.parseInt(String.valueOf(q));
        try {
            return ResponseEntity.ok(cartService.updateLineQuantity(email.trim(), orderItemId, qty));
        } catch (IllegalArgumentException ex) {
            if ("Forbidden".equals(ex.getMessage())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "message", ex.getMessage()));
            }
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    @PostMapping("/checkout")
    public ResponseEntity<?> checkout(@RequestHeader(value = "X-User-Email", required = false) String email,
                                      @RequestBody Map<String, Object> body) {
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Missing X-User-Email"));
        }
        Optional<ResponseEntity<?>> denied = verifyCustomer(email);
        if (denied.isPresent()) {
            return denied.get();
        }
        String paymentMethod = body != null && body.get("paymentMethod") != null
                ? String.valueOf(body.get("paymentMethod")) : null;
        String deliveryMode = body != null && body.get("deliveryMode") != null
                ? String.valueOf(body.get("deliveryMode")) : "ship";
        Long addressId = null;
        if (body != null && body.get("addressId") != null) {
            Object aid = body.get("addressId");
            addressId = aid instanceof Number ? ((Number) aid).longValue() : Long.parseLong(String.valueOf(aid));
        }
        try {
            return ResponseEntity.ok(cartService.checkout(email.trim(), paymentMethod, deliveryMode, addressId));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    @DeleteMapping("/items/{orderItemId}")
    public ResponseEntity<?> remove(@RequestHeader(value = "X-User-Email", required = false) String email,
                                    @PathVariable Long orderItemId) {
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Missing X-User-Email"));
        }
        Optional<ResponseEntity<?>> denied = verifyCustomer(email);
        if (denied.isPresent()) {
            return denied.get();
        }
        try {
            return ResponseEntity.ok(cartService.removeLine(email.trim(), orderItemId));
        } catch (IllegalArgumentException ex) {
            if ("Forbidden".equals(ex.getMessage())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "message", ex.getMessage()));
            }
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    private Optional<ResponseEntity<?>> verifyCustomer(String email) {
        Optional<Customer> customerOpt = customerRepository.findByEmail(email.trim());
        if (customerOpt.isPresent()) {
            return Optional.empty();
        }
        if (userRepository.findByEmail(email.trim()).isEmpty()) {
            return Optional.of(ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "User not found")));
        }
        return Optional.of(ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("success", false, "message", "Only customer accounts have a cart")));
    }
}
