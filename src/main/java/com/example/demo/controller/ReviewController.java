package com.example.demo.controller;

import com.example.demo.model.Customer;
import com.example.demo.model.Product;
import com.example.demo.model.Review;
import com.example.demo.model.User;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.ReviewRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.OrderItemRepository;
import com.example.demo.model.Order;
import com.example.demo.model.OrderItem;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private com.example.demo.service.NotificationService notificationService;

    @GetMapping("/product/{productId}")
    public ResponseEntity<?> getReviewsForProduct(@PathVariable Long productId) {
        List<Review> reviews = reviewRepository.findActiveByProductIdOrderByDateDesc(productId);
        Double avg = reviewRepository.findAverageRatingByProductId(productId);
        long count = reviewRepository.countByProductId(productId);

        return ResponseEntity.ok(Map.of(
                "reviews", reviews,
                "averageRating", avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0,
                "reviewCount", count));
    }

    @PostMapping("/product/{productId}")
    public ResponseEntity<?> submitReview(
            @PathVariable Long productId,
            @RequestBody Map<String, Object> payload) {

        String email = (String) payload.get("email");
        int rating = Integer.parseInt(payload.get("rating").toString());
        String comment = (String) payload.get("comment");

        if (rating < 1 || rating > 5) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Rating must be between 1 and 5"));
        }
        if (comment == null || comment.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Comment cannot be empty"));
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "User not found"));
        }
        if (!(userOpt.get() instanceof Customer)) {
            return ResponseEntity.status(403)
                    .body(Map.of("success", false, "message", "Only customers can leave reviews"));
        }

        Optional<Product> productOpt = productRepository.findById(productId);
        if (productOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Product not found"));
        }

        Optional<Review> existingReview = reviewRepository.findByProductIdAndCustomerEmail(productId, email);
        if (existingReview.isPresent()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "You have already reviewed this product."));
        }

        // Verify the customer actually purchased and received (delivered) this product
        Customer customer = (Customer) userOpt.get();
        List<Order> pastOrders = orderRepository.findByCustomerAndStatusNotIgnoreCaseOrderByDateDesc(customer, "cart");
        boolean eligibleToReview = false;
        for (Order o : pastOrders) {
            if (o.getStatus() != null && "delivered".equalsIgnoreCase(o.getStatus())) {
                if (o.getOrderItems() != null) {
                    for (OrderItem oi : o.getOrderItems()) {
                        if (oi.getProduct() != null && oi.getProduct().getId().equals(productId)) {
                            eligibleToReview = true;
                            break;
                        }
                    }
                }
            }
            if (eligibleToReview)
                break;
        }
        if (!eligibleToReview) {
            return ResponseEntity.status(403).body(
                    Map.of("success", false, "message", "You can only review products you purchased and received."));
        }

        Review review = new Review();
        review.setRating(rating);
        review.setComment(comment.trim());
        review.setDate(LocalDate.now());
        review.setCustomer((Customer) userOpt.get());
        review.setProduct(productOpt.get());

        reviewRepository.save(review);

        // Notify the product's artisan about the new review
        Product product = productOpt.get();
        if (product.getArtisan() != null) {
            String title = "New Customer Review";
            String msg = "A customer wrote a " + rating + "-star review on your product \"" + product.getTitle() + "\"";
            notificationService.sendNotification(
                    product.getArtisan(),
                    title,
                    msg,
                    "REVIEW",
                    "/artisanAnalytics?id=" + product.getArtisan().getUserId());
        }

        Double newAvg = reviewRepository.findAverageRatingByProductId(productId);
        long newCount = reviewRepository.countByProductId(productId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Review submitted successfully",
                "averageRating", newAvg != null ? Math.round(newAvg * 10.0) / 10.0 : 0.0,
                "reviewCount", newCount));
    }
}
