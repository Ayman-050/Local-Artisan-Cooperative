package com.example.demo.controller;

import com.example.demo.model.Customer;
import com.example.demo.model.Product;
import com.example.demo.model.Wishlist;
import com.example.demo.model.WishlistItem;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.WishlistItemRepository;
import com.example.demo.repository.WishlistRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/wishlist")
@Transactional
public class WishlistController {

    @Autowired
    private WishlistRepository wishlistRepository;

    @Autowired
    private WishlistItemRepository wishlistItemRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @GetMapping
    public ResponseEntity<?> getWishlist(@RequestHeader("X-User-Email") String email) {
        Optional<Wishlist> wishlistOpt = wishlistRepository.findByCustomer_Email(email);
        if (wishlistOpt.isPresent()) {
            List<Product> products = wishlistOpt.get().getWishlistItems().stream()
                    .map(WishlistItem::getProduct)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(products);
        }
        return ResponseEntity.ok(List.of());
    }

    @PostMapping("/add/{productId}")
    public ResponseEntity<?> addToWishlist(@RequestHeader("X-User-Email") String email, @PathVariable Long productId) {
        Optional<Customer> customerOpt = customerRepository.findByEmail(email);
        if (customerOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Customer not found");
        }
        Customer customer = customerOpt.get();

        Optional<Product> productOpt = productRepository.findById(productId);
        if (productOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Product not found");
        }

        Wishlist wishlist = wishlistRepository.findByCustomer(customer)
                .orElseGet(() -> {
                    Wishlist newWishlist = new Wishlist();
                    newWishlist.setCustomer(customer);
                    newWishlist.setDateCreated(LocalDate.now());
                    return wishlistRepository.save(newWishlist);
                });

        Optional<WishlistItem> existingItem = wishlistItemRepository.findByWishlistAndProduct(wishlist, productOpt.get());
        if (existingItem.isPresent()) {
            return ResponseEntity.ok("Item already in wishlist");
        }

        WishlistItem item = new WishlistItem();
        item.setWishlist(wishlist);
        item.setProduct(productOpt.get());
        item.setDateAdded(LocalDate.now());
        
        if (wishlist.getWishlistItems() == null) {
            wishlist.setWishlistItems(new java.util.ArrayList<>());
        }
        wishlist.getWishlistItems().add(item);

        wishlistItemRepository.save(item);
        wishlistRepository.save(wishlist);

        return ResponseEntity.ok("Item added to wishlist");
    }

    @DeleteMapping("/remove/{productId}")
    public ResponseEntity<?> removeFromWishlist(@RequestHeader("X-User-Email") String email, @PathVariable Long productId) {
        Optional<Wishlist> wishlistOpt = wishlistRepository.findByCustomer_Email(email);
        if (wishlistOpt.isPresent()) {
            Optional<Product> productOpt = productRepository.findById(productId);
            if (productOpt.isPresent()) {
                Wishlist wishlist = wishlistOpt.get();
                Optional<WishlistItem> itemOpt = wishlistItemRepository.findByWishlistAndProduct(wishlist, productOpt.get());
                if (itemOpt.isPresent()) {
                    WishlistItem item = itemOpt.get();
                    if (wishlist.getWishlistItems() != null) {
                        wishlist.getWishlistItems().remove(item);
                    }
                    wishlistItemRepository.delete(item);
                    wishlistRepository.save(wishlist);
                    return ResponseEntity.ok("Item removed from wishlist");
                } else {
                    return ResponseEntity.badRequest().body("Item not in wishlist");
                }
            }
        }
        return ResponseEntity.badRequest().body("Wishlist or product not found");
    }
    
    @GetMapping("/count")
    public ResponseEntity<?> getWishlistCount(@RequestHeader("X-User-Email") String email) {
        Optional<Wishlist> wishlistOpt = wishlistRepository.findByCustomer_Email(email);
        if (wishlistOpt.isPresent()) {
            return ResponseEntity.ok(wishlistOpt.get().getWishlistItems().size());
        }
        return ResponseEntity.ok(0);
    }
}
