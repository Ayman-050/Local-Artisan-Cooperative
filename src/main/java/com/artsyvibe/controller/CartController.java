package com.artsyvibe.controller;

import com.artsyvibe.security.CurrentUser;
import com.artsyvibe.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * The signed-in customer's cart. The customer comes from the session; the legacy X-User-Email header that
 * the front end still sends is ignored. Errors are mapped to JSON by ApiExceptionHandler.
 */
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    private final CurrentUser currentUser;

    @GetMapping
    public ResponseEntity<?> getCart() {
        return ResponseEntity.ok(cartService.getCartPayload(currentUser.requireCustomer()));
    }

    @PostMapping("/add")
    public ResponseEntity<?> add(@RequestBody Map<String, Object> body) {
        Long productId = RequestValues.requiredLong(body, "productId");
        int qty = RequestValues.optionalInt(body, "quantity", 1);
        return ResponseEntity.ok(cartService.addItem(currentUser.requireCustomer(), productId, qty));
    }

    @PatchMapping("/items/{orderItemId}")
    public ResponseEntity<?> updateQuantity(@PathVariable Long orderItemId, @RequestBody Map<String, Object> body) {
        int qty = RequestValues.optionalInt(body, "quantity", -1);
        if (qty == -1) {
            throw new IllegalArgumentException("quantity required");
        }
        return ResponseEntity.ok(cartService.updateLineQuantity(currentUser.requireCustomer(), orderItemId, qty));
    }

    @PostMapping("/checkout")
    public ResponseEntity<?> checkout(@RequestBody Map<String, Object> body) {
        String paymentMethod = RequestValues.optionalString(body, "paymentMethod");
        String deliveryMode = body != null && body.get("deliveryMode") != null
                ? String.valueOf(body.get("deliveryMode"))
                : "ship";
        Long addressId = RequestValues.optionalLong(body, "addressId");
        return ResponseEntity.ok(cartService.checkout(currentUser.requireCustomer(), paymentMethod, deliveryMode, addressId));
    }

    @DeleteMapping("/items/{orderItemId}")
    public ResponseEntity<?> remove(@PathVariable Long orderItemId) {
        return ResponseEntity.ok(cartService.removeLine(currentUser.requireCustomer(), orderItemId));
    }
}
