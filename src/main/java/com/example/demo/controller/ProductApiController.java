package com.example.demo.controller;

import com.example.demo.model.Artisan;
import com.example.demo.model.Auction;
import com.example.demo.model.Product;
import com.example.demo.model.ProductStatus;
import com.example.demo.model.Review;
import com.example.demo.model.Roles;
import com.example.demo.model.User;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.ReviewRepository;
import com.example.demo.security.CurrentUser;
import com.example.demo.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductApiController {

    private final ProductService productService;
    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;
    private final CurrentUser currentUser;

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getProduct(@PathVariable Long id) {
        Product product = productRepository.findById(id)
                .filter(this::isVisibleToCaller)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        List<Map<String, Object>> reviewsList = reviewRepository.findByProductIdWithCustomerOrderByDateDesc(id)
                .stream()
                .map(r -> Map.<String, Object>of(
                        "reviewerName", r.getCustomer() != null ? r.getCustomer().getName() : "Anonymous",
                        "reviewerImage", r.getCustomer() != null && r.getCustomer().getProfilePicture() != null
                                ? r.getCustomer().getProfilePicture()
                                : User.DEFAULT_PROFILE_PICTURE,
                        "rating", r.getRating(),
                        "comment", r.getComment() != null ? r.getComment() : "",
                        "date", r.getDate() != null ? r.getDate().toString() : ""))
                .toList();

        long totalProducts = 0;
        double avgRating = 0.0;
        long reviewsCount = 0;
        if (product.getArtisan() != null) {
            Long artisanId = product.getArtisan().getUserId();
            totalProducts = productRepository.countByArtisanUserId(artisanId);
            List<Review> artisanReviews = reviewRepository.findByProductArtisanUserId(artisanId).stream()
                    .filter(r -> r.getStatus() == null || !"rejected".equalsIgnoreCase(r.getStatus()))
                    .toList();
            reviewsCount = artisanReviews.size();
            // No reviews means no rating (previously reported as a perfect 5.0).
            avgRating = artisanReviews.stream().mapToInt(Review::getRating).average().orElse(0.0);
            avgRating = Math.round(avgRating * 10.0) / 10.0;
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "product", product,
                "reviews", reviewsList,
                "artisanTotalProducts", totalProducts,
                "artisanAverageRating", avgRating,
                "artisanReviewsCount", reviewsCount));
    }

    /** Creates a product (or an auction lot) for the signed-in artisan. Any artisanEmail in the body is ignored. */
    @PostMapping
    public ResponseEntity<?> createProduct(@RequestBody Map<String, Object> payload) {
        Artisan artisan = currentUser.requireArtisan();
        boolean isAuctionItem = RequestValues.optionalBoolean(payload, "isAuctionItem", false);
        LocalDateTime start = parseTime(RequestValues.optionalString(payload, "startTime"), "startTime");
        LocalDateTime end = parseTime(RequestValues.optionalString(payload, "endTime"), "endTime");
        Product saved = productService.createProduct(artisan, readInput(payload), isAuctionItem, start, end);
        return ResponseEntity.ok(Map.of("success", true, "product", saved));
    }

    @PostMapping("/auction")
    public ResponseEntity<?> listProductForAuction(@RequestBody Map<String, Object> payload) {
        Product product = findOwnedProduct(RequestValues.requiredLong(payload, "productId"));
        Auction auction = productService.listForAuction(product,
                RequestValues.requiredMoney(payload, "startingBid"),
                RequestValues.requiredInt(payload, "durationDays"));
        return ResponseEntity.ok(Map.of("success", true, "auction", auction));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateProduct(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Product updated = productService.updateProduct(findOwnedProduct(id), readInput(payload));
        return ResponseEntity.ok(Map.of("success", true, "product", updated));
    }

    /** Admin moderation: "hidden" hides the listing, anything else re-publishes it. */
    @PutMapping("/{id}/status")
    @Transactional
    public ResponseEntity<?> updateProductStatus(@PathVariable Long id, @RequestParam String status) {
        currentUser.requireAdmin();
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        product.setStatus(ProductStatus.HIDDEN.equalsIgnoreCase(status)
                ? ProductStatus.HIDDEN
                : ProductStatus.forStock(product.getStockQuantity()));
        return ResponseEntity.ok(Map.of("success", true, "product", product));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProduct(@PathVariable Long id) {
        boolean deleted = productService.deleteOrHide(findOwnedProduct(id));
        return ResponseEntity.ok(Map.of("success", true, "deleted", deleted,
                "message", deleted ? "Product deleted." : "Product has order history, so it was hidden instead."));
    }

    private Product findOwnedProduct(Long id) {
        Long artisanId = currentUser.requireArtisan().getUserId();
        return productRepository.findById(id)
                .filter(p -> p.getArtisan() != null && p.getArtisan().getUserId().equals(artisanId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    /** Hidden products are only visible to their artisan and to admins. */
    private boolean isVisibleToCaller(Product product) {
        if (!ProductStatus.HIDDEN.equalsIgnoreCase(product.getStatus())) {
            return true;
        }
        if (currentUser.hasRole(Roles.ADMIN)) {
            return true;
        }
        return currentUser.id()
                .map(id -> product.getArtisan() != null && id.equals(product.getArtisan().getUserId()))
                .orElse(false);
    }

    private static ProductService.ProductInput readInput(Map<String, Object> payload) {
        return new ProductService.ProductInput(
                RequestValues.requiredString(payload, "title"),
                RequestValues.optionalString(payload, "description"),
                RequestValues.requiredMoney(payload, "price"),
                RequestValues.optionalString(payload, "category"),
                RequestValues.optionalString(payload, "imageUrl"),
                RequestValues.optionalInt(payload, "stockQuantity", 0),
                RequestValues.optionalString(payload, "status"));
    }

    /** Accepts ISO instants ("...Z", from the browser) or local date-times ("2026-01-31T10:00"). */
    private static LocalDateTime parseTime(String value, String field) {
        if (value == null || value.isBlank() || "null".equals(value)) {
            return null;
        }
        try {
            if (value.endsWith("Z")) {
                return Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDateTime();
            }
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(field + " is not a valid date/time.");
        }
    }
}
