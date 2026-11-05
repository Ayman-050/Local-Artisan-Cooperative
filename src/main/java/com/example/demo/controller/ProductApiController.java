package com.example.demo.controller;

import com.example.demo.model.Product;
import com.example.demo.model.Artisan;
import com.example.demo.model.Auction;
import com.example.demo.service.ProductService;
import com.example.demo.service.AuctionService;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.ArtisanRepository;
import com.example.demo.repository.AuctionRepository;
import com.example.demo.repository.WishlistItemRepository;
import com.example.demo.repository.ReviewRepository;
import com.example.demo.model.Review;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductApiController {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ArtisanRepository artisanRepository;

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private WishlistItemRepository wishlistItemRepository;

    @Autowired
    private AuctionService auctionService;

    @Autowired
    private ReviewRepository reviewRepository;

    @GetMapping("/{id}")
    public ResponseEntity<?> getProduct(@PathVariable Long id) {
        return productRepository.findById(id)
                .map(product -> {
                    // Fetch reviews
                    List<Review> reviews = reviewRepository.findByProductIdWithCustomerOrderByDateDesc(id);
                    List<Map<String, Object>> reviewsList = reviews.stream().map(r -> Map.<String, Object>of(
                            "reviewerName", r.getCustomer() != null ? r.getCustomer().getName() : "Anonymous",
                            "reviewerImage", r.getCustomer() != null && r.getCustomer().getProfilePicture() != null
                                    ? r.getCustomer().getProfilePicture()
                                    : "https://cdn-icons-png.flaticon.com/512/149/149071.png",
                            "rating", r.getRating(),
                            "comment", r.getComment(),
                            "date", r.getDate() != null ? r.getDate().toString() : "")).toList();

                    // Calculate artisan info if present
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
                        if (reviewsCount > 0) {
                            avgRating = artisanReviews.stream().mapToInt(Review::getRating).average().orElse(0.0);
                        } else {
                            avgRating = 5.0; // fallback if no reviews
                        }
                    }

                    // Build dynamic response Map
                    return ResponseEntity.ok(Map.of(
                            "success", true,
                            "product", product,
                            "reviews", reviewsList,
                            "artisanTotalProducts", totalProducts,
                            "artisanAverageRating", avgRating,
                            "artisanReviewsCount", reviewsCount));
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "message", "Product not found")));
    }

    @PostMapping
    public ResponseEntity<?> createProduct(@RequestBody Map<String, Object> payload) {
        try {
            String email = (String) payload.get("artisanEmail");
            String title = (String) payload.get("title");
            String description = (String) payload.get("description");
            double price = Double.parseDouble(payload.get("price").toString());
            String category = (String) payload.get("category");
            String imageUrl = (String) payload.get("imageUrl");
            int stockQuantity = Integer.parseInt(payload.get("stockQuantity").toString());
            boolean isAuctionItem = Boolean.parseBoolean(payload.get("isAuctionItem").toString());

            Artisan artisan = artisanRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("Artisan not found"));

            if (!"active".equalsIgnoreCase(artisan.getStatus())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("success", false, "message", "Your artisan account status is currently "
                                + artisan.getStatus() + ". You cannot perform this action until approved."));
            }

            Product product = new Product();
            product.setArtisan(artisan);
            product.setArtisanName(artisan.getName());
            product.setTitle(title);
            product.setDescription(description);
            product.setPrice(price);
            product.setCategory(category);
            product.setImageUrl(imageUrl);
            product.setStockQuantity(stockQuantity);
            String status = payload.get("status") != null ? (String) payload.get("status")
                    : (stockQuantity > 0 ? "active" : "out of stock");
            product.setStatus(status);
            product.setAuctionItem(isAuctionItem);
            product.setAddingDate(java.time.LocalDate.now());

            Product saved = productRepository.save(product);

            if (isAuctionItem) {
                Auction auction = new Auction();
                auction.setProduct(saved);
                auction.setStartingBid(price);
                auction.setCurrentHighestBid(price);

                java.time.LocalDateTime now = java.time.LocalDateTime.now();
                java.time.LocalDateTime start = now;
                String startTimeStr = (String) payload.get("startTime");
                if (startTimeStr != null && !startTimeStr.isBlank()) {
                    try {
                        if (startTimeStr.endsWith("Z")) {
                            start = java.time.Instant.parse(startTimeStr).atZone(java.time.ZoneId.systemDefault())
                                    .toLocalDateTime();
                        } else {
                            start = java.time.LocalDateTime.parse(startTimeStr);
                        }
                    } catch (Exception ex) {
                    }
                }
                auction.setStartTime(start);

                java.time.LocalDateTime end = start.plusDays(7);
                String endTimeStr = (String) payload.get("endTime");
                if (endTimeStr != null && !endTimeStr.isBlank()) {
                    try {
                        if (endTimeStr.endsWith("Z")) {
                            end = java.time.Instant.parse(endTimeStr).atZone(java.time.ZoneId.systemDefault())
                                    .toLocalDateTime();
                        } else {
                            end = java.time.LocalDateTime.parse(endTimeStr);
                        }
                    } catch (Exception ex) {
                    }
                }
                auction.setEndTime(end);

                if (start.isAfter(now)) {
                    auction.setStatus("UPCOMING");
                } else {
                    auction.setStatus("LIVE");
                }

                Auction savedAuction = auctionRepository.save(auction);

                if ("LIVE".equals(savedAuction.getStatus())) {
                    try {
                        auctionService.triggerAuctionStartNotification(savedAuction);
                    } catch (Exception ex) {
                        // Ignore
                    }
                }
            }

            return ResponseEntity.ok(Map.of("success", true, "product", saved));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/auction")
    public ResponseEntity<?> listProductForAuction(@RequestBody Map<String, Object> payload) {
        try {
            Long productId = Long.parseLong(payload.get("productId").toString());
            double startingBid = Double.parseDouble(payload.get("startingBid").toString());
            int durationDays = Integer.parseInt(payload.get("durationDays").toString());

            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found"));

            if (product.getArtisan() != null && !"active".equalsIgnoreCase(product.getArtisan().getStatus())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("success", false, "message", "Your artisan account is not active."));
            }

            product.setAuctionItem(true);
            product.setPrice(startingBid);
            Product savedProduct = productRepository.save(product);

            Auction auction = new Auction();
            auction.setProduct(savedProduct);
            auction.setStartingBid(startingBid);
            auction.setCurrentHighestBid(startingBid);
            auction.setStartTime(java.time.LocalDateTime.now());
            auction.setEndTime(java.time.LocalDateTime.now().plusDays(durationDays));
            auction.setStatus("LIVE");

            Auction savedAuction = auctionRepository.save(auction);

            try {
                auctionService.triggerAuctionStartNotification(savedAuction);
            } catch (Exception ex) {
                // Ignore
            }

            return ResponseEntity.ok(Map.of("success", true, "auction", savedAuction));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateProduct(@PathVariable Long id, @RequestBody Product product) {
        try {
            Product existing = productRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found"));
            if (existing.getArtisan() != null && !"active".equalsIgnoreCase(existing.getArtisan().getStatus())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("success", false, "message",
                                "Your artisan account is not active. Access denied."));
            }
            Product updated = productService.updateProduct(id, product);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateProductStatus(@PathVariable Long id, @RequestParam String status) {
        try {
            return productRepository.findById(id)
                    .map(product -> {
                        if ("hidden".equalsIgnoreCase(status)) {
                            product.setStatus("hidden");
                        } else {
                            // "approve": reset to active/out of stock depending on stockQuantity
                            product.setStatus(product.getStockQuantity() > 0 ? "active" : "out of stock");
                        }
                        Product saved = productRepository.save(product);
                        return ResponseEntity.ok(Map.of("success", true, "product", saved));
                    })
                    .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(Map.of("success", false, "message", "Product not found")));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProduct(@PathVariable Long id) {
        try {
            Product existing = productRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found"));
            if (existing.getArtisan() != null && !"active".equalsIgnoreCase(existing.getArtisan().getStatus())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("success", false, "message",
                                "Your artisan account is not active. Access denied."));
            }

            productRepository.findById(id).ifPresent(product -> {
                // Delete associated wishlist items
                var wishlistItems = wishlistItemRepository.findByProduct(product);
                wishlistItemRepository.deleteAll(wishlistItems);

                // Delete associated auctions
                var auctions = auctionRepository.findAll().stream()
                        .filter(a -> a.getProduct() != null && a.getProduct().getId().equals(id))
                        .toList();
                auctionRepository.deleteAll(auctions);
            });

            productRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
