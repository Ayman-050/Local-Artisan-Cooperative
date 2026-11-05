package com.artsyvibe.controller;

import com.artsyvibe.model.Customer;
import com.artsyvibe.model.Product;
import com.artsyvibe.model.Review;
import com.artsyvibe.repository.OrderItemRepository;
import com.artsyvibe.repository.ProductRepository;
import com.artsyvibe.repository.ReviewRepository;
import com.artsyvibe.security.CurrentUser;
import com.artsyvibe.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private static final int MAX_COMMENT_LENGTH = 2000;

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final NotificationService notificationService;
    private final CurrentUser currentUser;

    @GetMapping("/product/{productId}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getReviewsForProduct(@PathVariable Long productId) {
        List<Review> reviews = reviewRepository.findActiveByProductIdOrderByDateDesc(productId);
        return ResponseEntity.ok(Map.of(
                "reviews", reviews,
                "averageRating", roundedAverage(productId),
                "reviewCount", reviewRepository.countByProductId(productId)));
    }

    /** A customer reviews a product they bought and received. The reviewer comes from the session. */
    @PostMapping("/product/{productId}")
    @Transactional
    public ResponseEntity<?> submitReview(@PathVariable Long productId, @RequestBody Map<String, Object> payload) {
        Customer customer = currentUser.requireCustomer();
        int rating = RequestValues.requiredInt(payload, "rating");
        String comment = RequestValues.optionalString(payload, "comment");

        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }
        if (comment == null || comment.isBlank()) {
            throw new IllegalArgumentException("Comment cannot be empty");
        }
        if (comment.length() > MAX_COMMENT_LENGTH) {
            throw new IllegalArgumentException("Comment is too long (" + MAX_COMMENT_LENGTH + " characters max).");
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        if (reviewRepository.findByProductIdAndCustomerEmail(productId, customer.getEmail()).isPresent()) {
            throw new IllegalArgumentException("You have already reviewed this product.");
        }
        if (!orderItemRepository.hasDeliveredPurchase(customer.getUserId(), productId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can only review products you purchased and received.");
        }

        Review review = new Review();
        review.setRating(rating);
        review.setComment(comment.trim());
        review.setDate(LocalDate.now());
        review.setStatus("active");
        review.setAutoFlagged(false);
        review.setCustomer(customer);
        review.setProduct(product);
        reviewRepository.save(review);

        if (product.getArtisan() != null) {
            notificationService.sendNotification(product.getArtisan(), "New Customer Review",
                    "A customer wrote a " + rating + "-star review on your product \"" + product.getTitle() + "\"",
                    "REVIEW", "/artisanAnalytics");
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Review submitted successfully",
                "averageRating", roundedAverage(productId),
                "reviewCount", reviewRepository.countByProductId(productId)));
    }

    private double roundedAverage(Long productId) {
        Double avg = reviewRepository.findAverageRatingByProductId(productId);
        return avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0;
    }
}
