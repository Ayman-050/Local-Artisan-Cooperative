package com.artsyvibe.controller;

import com.artsyvibe.model.Customer;
import com.artsyvibe.model.Product;
import com.artsyvibe.model.Wishlist;
import com.artsyvibe.model.WishlistItem;
import com.artsyvibe.repository.ProductRepository;
import com.artsyvibe.repository.WishlistItemRepository;
import com.artsyvibe.repository.WishlistRepository;
import com.artsyvibe.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The signed-in customer's wishlist (the legacy X-User-Email header is ignored). */
@RestController
@RequestMapping("/api/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistRepository wishlistRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final ProductRepository productRepository;
    private final CurrentUser currentUser;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> getWishlist() {
        List<Product> products = wishlistRepository.findByCustomer(currentUser.requireCustomer())
                .map(w -> w.getWishlistItems() == null ? List.<Product>of()
                        : w.getWishlistItems().stream().map(WishlistItem::getProduct).toList())
                .orElse(List.of());
        return ResponseEntity.ok(products);
    }

    @PostMapping("/add/{productId}")
    @Transactional
    public ResponseEntity<?> addToWishlist(@PathVariable Long productId) {
        Customer customer = currentUser.requireCustomer();
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        Wishlist wishlist = wishlistRepository.findByCustomer(customer).orElseGet(() -> {
            Wishlist fresh = new Wishlist();
            fresh.setCustomer(customer);
            fresh.setDateCreated(LocalDate.now());
            fresh.setWishlistItems(new ArrayList<>());
            return wishlistRepository.save(fresh);
        });

        if (wishlistItemRepository.findByWishlistAndProduct(wishlist, product).isPresent()) {
            return ResponseEntity.ok("Item already in wishlist");
        }
        WishlistItem item = new WishlistItem();
        item.setWishlist(wishlist);
        item.setProduct(product);
        item.setDateAdded(LocalDate.now());
        wishlistItemRepository.save(item);
        return ResponseEntity.ok("Item added to wishlist");
    }

    @DeleteMapping("/remove/{productId}")
    @Transactional
    public ResponseEntity<?> removeFromWishlist(@PathVariable Long productId) {
        Customer customer = currentUser.requireCustomer();
        Optional<Wishlist> wishlist = wishlistRepository.findByCustomer(customer);
        Optional<Product> product = productRepository.findById(productId);
        if (wishlist.isEmpty() || product.isEmpty()) {
            return ResponseEntity.badRequest().body("Wishlist or product not found");
        }
        Optional<WishlistItem> item = wishlistItemRepository.findByWishlistAndProduct(wishlist.get(), product.get());
        if (item.isEmpty()) {
            return ResponseEntity.badRequest().body("Item not in wishlist");
        }
        if (wishlist.get().getWishlistItems() != null) {
            wishlist.get().getWishlistItems().remove(item.get());
        }
        wishlistItemRepository.delete(item.get());
        return ResponseEntity.ok("Item removed from wishlist");
    }

    @GetMapping("/count")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getWishlistCount() {
        int count = wishlistRepository.findByCustomer(currentUser.requireCustomer())
                .map(w -> w.getWishlistItems() == null ? 0 : w.getWishlistItems().size())
                .orElse(0);
        return ResponseEntity.ok(count);
    }
}
