package com.artsyvibe.service;

import com.artsyvibe.model.Artisan;
import com.artsyvibe.model.Auction;
import com.artsyvibe.model.Notification;
import com.artsyvibe.model.OrderItem;
import com.artsyvibe.model.Product;
import com.artsyvibe.model.RefundStatus;
import com.artsyvibe.model.Review;
import com.artsyvibe.model.Roles;
import com.artsyvibe.model.User;
import com.artsyvibe.model.UserStatus;
import com.artsyvibe.repository.ArtisanRepository;
import com.artsyvibe.repository.AuctionRepository;
import com.artsyvibe.repository.NotificationRepository;
import com.artsyvibe.repository.OrderItemRepository;
import com.artsyvibe.repository.ProductRepository;
import com.artsyvibe.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class NotificationService {

    /** The admin backfill scans several tables; don't repeat it on every 30-second badge poll. */
    private static final Duration ADMIN_BACKFILL_INTERVAL = Duration.ofMinutes(5);

    private final NotificationRepository notificationRepository;
    private final ArtisanRepository artisanRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final AuctionRepository auctionRepository;

    private final Map<Long, Instant> lastAdminBackfill = new ConcurrentHashMap<>();

    public void sendNotification(User user, String title, String message, String type, String link) {
        notificationRepository.save(new Notification(user, title, message, type, link));
    }

    @Transactional
    public List<Notification> getNotificationsForUser(User user) {
        ensureAdminNotifications(user);
        return notificationRepository.findByUserOrderByTimestampDesc(user);
    }

    @Transactional
    public long getUnreadCount(User user) {
        ensureAdminNotifications(user);
        return notificationRepository.countByUserAndIsReadFalse(user);
    }

    @Transactional
    public void markAsRead(User owner, Long notificationId) {
        findOwned(owner, notificationId).setRead(true);
    }

    @Transactional
    public void markAllAsRead(User user) {
        notificationRepository.markAllReadForUser(user.getUserId());
    }

    @Transactional
    public void deleteNotification(User owner, Long notificationId) {
        notificationRepository.delete(findOwned(owner, notificationId));
    }

    private Notification findOwned(User owner, Long notificationId) {
        return notificationRepository.findByIdAndUser_UserId(notificationId, owner.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
    }

    /**
     * Admins get a notification for every item that needs their attention (pending artisans, refunds,
     * flagged reviews, products awaiting verification, ended auctions), created lazily the first time it is
     * noticed. Throttled per admin. Duplicates are detected by matching link + message text, which is
     * brittle; a proper fix stores the source entity id on the notification.
     */
    private void ensureAdminNotifications(User admin) {
        if (admin == null || !Roles.ADMIN.equalsIgnoreCase(admin.getRole())) {
            return;
        }
        Instant now = Instant.now();
        Instant last = lastAdminBackfill.get(admin.getUserId());
        if (last != null && Duration.between(last, now).compareTo(ADMIN_BACKFILL_INTERVAL) < 0) {
            return;
        }
        lastAdminBackfill.put(admin.getUserId(), now);

        List<Notification> existing = notificationRepository.findByUserOrderByTimestampDesc(admin);

        List<Artisan> pendingArtisans = artisanRepository.findByStatusIn(
                List.of(UserStatus.PENDING_APPROVAL, UserStatus.PENDING));
        for (Artisan artisan : pendingArtisans) {
            String link = "/adminArtisanApproval";
            String shopName = artisan.getShopName() != null ? artisan.getShopName() : "Shop";
            String message = artisan.getName() + " has submitted an artisan application for shop \""
                    + shopName + "\". Portfolio verification required.";
            if (!exists(existing, link, artisan.getName())) {
                sendNotification(admin, "New Artisan Application Pending", message, "ARTISAN_APPROVAL", link);
            }
        }

        for (OrderItem item : orderItemRepository.findByRefundRequestedTrueAndAdminRefundStatusIgnoreCase(RefundStatus.PENDING)) {
            String link = "/adminRefund";
            boolean escalated = RefundStatus.ESCALATED.equalsIgnoreCase(item.getRefundStatus());
            String orderIdStr = item.getOrder() != null ? String.valueOf(item.getOrder().getOrderId()) : "";
            String prodTitle = item.getProduct() != null ? item.getProduct().getTitle() : "Product";
            String message = "Refund request for item \"" + prodTitle + "\" (Order #" + orderIdStr
                    + ") requires administrator review and final decision.";
            if (!exists(existing, link, "Order #" + orderIdStr)) {
                sendNotification(admin, escalated ? "Refund Request Escalated" : "Refund Requires Review", message,
                        escalated ? "REFUND_ESCALATED" : "REFUND_ADMIN_REVIEW", link);
            }
        }

        for (Review review : reviewRepository.findByAutoFlaggedTrue()) {
            if ("approved".equalsIgnoreCase(review.getStatus())
                    || "rejected".equalsIgnoreCase(review.getStatus())) {
                continue;
            }
            String link = "/adminReview";
            String prodTitle = review.getProduct() != null ? review.getProduct().getTitle() : "Product";
            String custName = review.getCustomer() != null ? review.getCustomer().getName() : "Customer";
            String message = "Review for \"" + prodTitle + "\" by " + custName
                    + " has been auto-flagged for offensive language. Manual moderation required.";
            if (!exists(existing, link, "\"" + prodTitle + "\"", custName)) {
                sendNotification(admin, "Flagged Review Awaiting Moderation", message, "REVIEW_FLAGGED", link);
            }
        }

        for (Product product : productRepository.findPendingVerification()) {
            String link = "/adminProducts";
            String msg = "New product \"" + product.getTitle() + "\" by "
                    + (product.getArtisanName() != null ? product.getArtisanName() : "Artisan")
                    + " is awaiting verification.";
            if (!exists(existing, link, "\"" + product.getTitle() + "\"")) {
                sendNotification(admin, "Product Verification Required", msg, "PRODUCT_VERIFICATION", link);
            }
        }

        for (Auction auction : auctionRepository.findByStatusIgnoreCase(AuctionService.DISPLAY_ENDED)) {
            String link = "/adminAuction";
            String prodTitle = auction.getProduct() != null ? auction.getProduct().getTitle() : "Product";
            String msg = "Auction for \"" + prodTitle + "\" has ended. Winning bid: "
                    + auction.getCurrentHighestBid().stripTrailingZeros().toPlainString() + " BD.";
            if (!exists(existing, link, "\"" + prodTitle + "\"")) {
                sendNotification(admin, "Auction Ended Successfully", msg, "AUCTION_ENDED", link);
            }
        }
    }

    private static boolean exists(List<Notification> existing, String link, String... fragments) {
        return existing.stream().anyMatch(n -> {
            if (!link.equals(n.getLink()) || n.getMessage() == null) {
                return false;
            }
            for (String f : fragments) {
                if (!n.getMessage().contains(f)) {
                    return false;
                }
            }
            return true;
        });
    }
}
