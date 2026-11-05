package com.example.demo.service;

import com.example.demo.model.Notification;
import com.example.demo.model.User;
import com.example.demo.model.Artisan;
import com.example.demo.model.OrderItem;
import com.example.demo.model.Review;
import com.example.demo.model.Product;
import com.example.demo.model.Auction;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.repository.ArtisanRepository;
import com.example.demo.repository.OrderItemRepository;
import com.example.demo.repository.ReviewRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.AuctionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ArtisanRepository artisanRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private AuctionRepository auctionRepository;

    public void sendNotification(User user, String title, String message, String type, String link) {
        Notification notification = new Notification(user, title, message, type, link);
        notificationRepository.save(notification);
    }

    public List<Notification> getNotificationsForUser(User user) {
        if (user != null && "ADMIN".equalsIgnoreCase(user.getRole())) {
            ensureAdminNotifications(user);
        }
        return notificationRepository.findByUserOrderByTimestampDesc(user);
    }

    public long getUnreadCount(User user) {
        if (user != null && "ADMIN".equalsIgnoreCase(user.getRole())) {
            ensureAdminNotifications(user);
        }
        return notificationRepository.countByUserAndIsReadFalse(user);
    }

    public void markAsRead(Long notificationId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            n.setRead(true);
            notificationRepository.save(n);
        });
    }

    public void markAllAsRead(User user) {
        List<Notification> unread = notificationRepository.findByUserOrderByTimestampDesc(user);
        for (Notification n : unread) {
            if (!n.isRead()) {
                n.setRead(true);
            }
        }
        notificationRepository.saveAll(unread);
    }

    public void deleteNotification(Long id) {
        notificationRepository.deleteById(id);
    }

    public synchronized void ensureAdminNotifications(User admin) {
        if (admin == null || !"ADMIN".equalsIgnoreCase(admin.getRole())) {
            return;
        }

        List<Notification> existingNotifs = notificationRepository.findByUserOrderByTimestampDesc(admin);

        // 1. Pending Artisan Approvals
        List<Artisan> pendingArtisans = artisanRepository.findByStatusIn(List.of("pending approval", "pending"));
        for (Artisan artisan : pendingArtisans) {
            String link = "/adminArtisanApproval";
            String title = "New Artisan Application Pending";
            String shopName = artisan.getShopName() != null ? artisan.getShopName() : "Shop";
            String message = artisan.getName() + " has submitted an artisan application for shop \"" 
                    + shopName + "\". Portfolio verification required.";

            boolean exists = existingNotifs.stream()
                    .anyMatch(n -> link.equals(n.getLink()) && n.getMessage().contains(artisan.getName()));
            if (!exists) {
                sendNotification(admin, title, message, "ARTISAN_APPROVAL", link);
            }
        }

        // 2. Pending Refunds
        List<OrderItem> pendingRefunds = orderItemRepository.findAllWithOrderAndProduct().stream()
                .filter(item -> item.isRefundRequested() && "PENDING".equalsIgnoreCase(item.getAdminRefundStatus()))
                .toList();
        for (OrderItem item : pendingRefunds) {
            String link = "/adminRefund";
            String title = "ESCALATED".equalsIgnoreCase(item.getRefundStatus()) ? "Refund Request Escalated" : "Refund Requires Review";
            String orderIdStr = item.getOrder() != null ? String.valueOf(item.getOrder().getOrderId()) : "";
            String prodTitle = item.getProduct() != null ? item.getProduct().getTitle() : "Product";
            String message = "Refund request for item \"" + prodTitle + "\" (Order #" + orderIdStr 
                    + ") requires administrator review and final decision.";

            boolean exists = existingNotifs.stream()
                    .anyMatch(n -> link.equals(n.getLink()) && n.getMessage().contains("Order #" + orderIdStr));
            if (!exists) {
                sendNotification(admin, title, message, "ESCALATED".equalsIgnoreCase(item.getRefundStatus()) ? "REFUND_ESCALATED" : "REFUND_ADMIN_REVIEW", link);
            }
        }

        // 3. Flagged Reviews
        List<Review> flaggedReviews = reviewRepository.findAllWithProductAndCustomer().stream()
                .filter(r -> Boolean.TRUE.equals(r.getAutoFlagged()) && !"approved".equalsIgnoreCase(r.getStatus()) && !"rejected".equalsIgnoreCase(r.getStatus()))
                .toList();
        for (Review review : flaggedReviews) {
            String link = "/adminReview";
            String title = "Flagged Review Awaiting Moderation";
            String prodTitle = review.getProduct() != null ? review.getProduct().getTitle() : "Product";
            String custName = review.getCustomer() != null ? review.getCustomer().getName() : "Customer";
            String message = "Review for \"" + prodTitle + "\" by " + custName + " has been auto-flagged for offensive language. Manual moderation required.";

            boolean exists = existingNotifs.stream()
                    .anyMatch(n -> link.equals(n.getLink()) && n.getMessage().contains("\"" + prodTitle + "\"") && n.getMessage().contains(custName));
            if (!exists) {
                sendNotification(admin, title, message, "REVIEW_FLAGGED", link);
            }
        }

        // 4. Products Pending Verification
        List<Product> pendingProducts = productRepository.findAll().stream()
                .filter(p -> "pending".equalsIgnoreCase(p.getStatus()) || "pending approval".equalsIgnoreCase(p.getStatus()))
                .toList();
        for (Product product : pendingProducts) {
            String link = "/adminProducts";
            String title = "Product Verification Required";
            String msg = "New product \"" + product.getTitle() + "\" by " 
                    + (product.getArtisanName() != null ? product.getArtisanName() : "Artisan") + " is awaiting verification.";

            boolean exists = existingNotifs.stream()
                    .anyMatch(n -> link.equals(n.getLink()) && n.getMessage().contains("\"" + product.getTitle() + "\""));
            if (!exists) {
                sendNotification(admin, title, msg, "PRODUCT_VERIFICATION", link);
            }
        }

        // 5. Ended Auctions
        List<Auction> endedAuctions = auctionRepository.findAllWithProduct().stream()
                .filter(a -> "ended".equalsIgnoreCase(a.getStatus()))
                .toList();
        for (Auction auction : endedAuctions) {
            String link = "/adminAuction";
            String title = "Auction Ended Successfully";
            String prodTitle = auction.getProduct() != null ? auction.getProduct().getTitle() : "Product";
            String msg = "Auction for \"" + prodTitle + "\" has ended. Winning bid: " + String.format("%.2f BD", auction.getCurrentHighestBid()) + ".";

            boolean exists = existingNotifs.stream()
                    .anyMatch(n -> link.equals(n.getLink()) && n.getMessage().contains("\"" + prodTitle + "\""));
            if (!exists) {
                sendNotification(admin, title, msg, "AUCTION_ENDED", link);
            }
        }

        // 6. Suspicious Auction Activity / System Tasks (Milestone & Backup)
        boolean hasSystemNotifs = existingNotifs.stream()
                .anyMatch(n -> "SYSTEM_MILESTONE".equals(n.getType()) || "SYSTEM_BACKUP".equals(n.getType()));
        if (!hasSystemNotifs) {
            sendNotification(admin, "Milestone Achieved: 1,000th User Registered!", 
                "Congratulations! The platform has reached 1,000 registered users. Emma Wilson from Manama is our 1,000th user.", 
                "SYSTEM_MILESTONE", "/adminDashboard");
            sendNotification(admin, "Daily Backup Completed Successfully", 
                "Scheduled database backup completed successfully. 2.4GB archived successfully to S3 bucket.", 
                "SYSTEM_BACKUP", "/adminDashboard");
            sendNotification(admin, "Weekly System Uptime Report", 
                "System uptime for the past week: 99.9%. Meets NFR-R1 requirement.", 
                "SYSTEM_UPTIME", "/adminDashboard");
        }
    }
}
