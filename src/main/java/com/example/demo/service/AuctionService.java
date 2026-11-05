package com.example.demo.service;

import com.example.demo.model.Auction;
import com.example.demo.model.Bid;
import com.example.demo.model.Customer;
import com.example.demo.model.Product;
import com.example.demo.model.Review;
import com.example.demo.model.Roles;
import com.example.demo.repository.AuctionRepository;
import com.example.demo.repository.BidRepository;
import com.example.demo.repository.ReviewRepository;
import com.example.demo.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuctionService {

    public static final String DISPLAY_LIVE = "LIVE";
    public static final String DISPLAY_UPCOMING = "UPCOMING";
    public static final String DISPLAY_ENDED = "ENDED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** A bid in the last N seconds pushes the end time back (anti-sniping). */
    private static final long ANTI_SNIPE_WINDOW_SECONDS = 30;
    private static final long ANTI_SNIPE_EXTENSION_MINUTES = 2;
    private static final int MAX_ADMIN_EXTENSION_MINUTES = 7 * 24 * 60;

    private final AuctionRepository auctionRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final BidRepository bidRepository;
    private final ReviewRepository reviewRepository;
    private final EntityManager entityManager;

    /** Status derived from the clock, which is what users see. The stored status may lag by one sync tick. */
    public String resolveDisplayStatus(Auction auction) {
        if (auction == null) {
            return DISPLAY_ENDED;
        }
        LocalDateTime now = LocalDateTime.now();
        String stored = normalizeStatus(auction.getStatus());

        if (STATUS_CANCELLED.equals(stored) || "DELETED".equals(stored)) {
            return STATUS_CANCELLED;
        }
        if (DISPLAY_ENDED.equals(stored)) {
            return DISPLAY_ENDED;
        }
        if (auction.getEndTime() != null && now.isAfter(auction.getEndTime())) {
            return DISPLAY_ENDED;
        }
        if (auction.getStartTime() != null && now.isBefore(auction.getStartTime())) {
            return DISPLAY_UPCOMING;
        }
        return DISPLAY_LIVE;
    }

    public static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "";
        }
        String upper = status.trim().toUpperCase();
        return "ACTIVE".equals(upper) ? DISPLAY_LIVE : upper;
    }

    public Optional<Auction> findById(Long id) {
        return auctionRepository.findById(id);
    }

    public Map<String, Object> toDetailMap(Auction auction) {
        Map<String, Object> map = new HashMap<>();
        String displayStatus = resolveDisplayStatus(auction);
        map.put("id", auction.getId());
        map.put("displayStatus", displayStatus);
        map.put("startingBid", wholeBd(auction.getStartingBid()));
        map.put("currentHighestBid", wholeBd(auction.getCurrentHighestBid()));
        map.put("highestBidderName", auction.getHighestBidderName());
        map.put("startTime", auction.getStartTime() != null ? auction.getStartTime().toString() : null);
        map.put("endTime", auction.getEndTime() != null ? auction.getEndTime().toString() : null);
        map.put("minNextBid", computeMinNextBid(auction));

        long secondsRemaining = 0;
        if (auction.getEndTime() != null && DISPLAY_LIVE.equals(displayStatus)) {
            secondsRemaining = Math.max(0, ChronoUnit.SECONDS.between(LocalDateTime.now(), auction.getEndTime()));
        }
        map.put("secondsRemaining", secondsRemaining);

        if (auction.getProduct() != null) {
            map.put("productId", auction.getProduct().getId());
            map.put("name", auction.getProduct().getTitle());
            map.put("artist", auction.getProduct().getArtisanName());
            map.put("category", auction.getProduct().getCategory());
            map.put("imageUrl", auction.getProduct().getImageUrl());
            map.put("description", auction.getProduct().getDescription());
        }
        return map;
    }

    /** Bids are whole BD. The first bid may equal the starting bid; later bids must beat the current one by 1 BD. */
    public int computeMinNextBid(Auction auction) {
        int starting = wholeBd(auction.getStartingBid());
        if (!hasBids(auction)) {
            return starting;
        }
        return Math.max(starting, wholeBd(auction.getCurrentHighestBid()) + 1);
    }

    private static boolean hasBids(Auction auction) {
        return auction.getHighestBidder() != null
                || (auction.getHighestBidderName() != null && !auction.getHighestBidderName().isBlank());
    }

    private static int wholeBd(BigDecimal amount) {
        return amount == null ? 0 : amount.setScale(0, RoundingMode.FLOOR).intValueExact();
    }

    /**
     * Places a bid for a signed-in customer. The auction row is locked for the duration of the transaction,
     * so two simultaneous bids are applied one after the other and the second one is validated against the
     * first (previously both could pass the "minimum bid" check and the lower one could win).
     */
    @Transactional
    public Map<String, Object> placeBid(Long auctionId, int bidAmount, Customer bidder) {
        Map<String, Object> result = new HashMap<>();
        Auction auction = lockAuction(auctionId);
        if (auction == null) {
            return fail(result, "Auction not found.");
        }

        String displayStatus = resolveDisplayStatus(auction);
        if (DISPLAY_ENDED.equals(displayStatus) || STATUS_CANCELLED.equals(displayStatus)) {
            return fail(result, "Auction has already ended.");
        }
        if (DISPLAY_UPCOMING.equals(displayStatus)) {
            return fail(result, "This auction has not started yet.");
        }
        Customer previousLeader = auction.getHighestBidder();
        if (previousLeader != null && previousLeader.getUserId().equals(bidder.getUserId())) {
            return fail(result, "You are already the highest bidder.");
        }

        int minBid = computeMinNextBid(auction);
        if (bidAmount < minBid) {
            return fail(result, "Your bid must be at least " + minBid + " BD (current bid + 1 BD minimum).");
        }

        if (previousLeader != null) {
            notificationService.sendNotification(previousLeader, "Outbid!",
                    "Someone has placed a higher bid of " + bidAmount + " BD on " + auction.getProduct().getTitle(),
                    "BID_OUTBID", "/ProductDetailsAuction?id=" + auction.getId());
        }

        boolean extended = false;
        LocalDateTime now = LocalDateTime.now();
        long secondsRemaining = ChronoUnit.SECONDS.between(now, auction.getEndTime());
        if (secondsRemaining <= ANTI_SNIPE_WINDOW_SECONDS && secondsRemaining >= 0) {
            auction.setEndTime(auction.getEndTime().plusMinutes(ANTI_SNIPE_EXTENSION_MINUTES));
            extended = true;
        }

        BigDecimal amount = BigDecimal.valueOf(bidAmount);
        auction.setCurrentHighestBid(amount);
        auction.setHighestBidderName(bidder.getName());
        auction.setHighestBidder(bidder);
        auction.setStatus(DISPLAY_LIVE);
        auctionRepository.save(auction);

        Bid bid = new Bid();
        bid.setAuction(auction);
        bid.setBidAmount(amount);
        bid.setBidTime(now);
        bid.setCustomer(bidder);
        bidRepository.save(bid);

        result.put("success", true);
        result.put("message", extended
                ? "Bid placed! Anti-snipe rule applied: the auction was extended by 2 minutes."
                : "Bid placed successfully!");
        result.put("extended", extended);
        result.put("auction", toDetailMap(auction));
        return result;
    }

    /**
     * Loads the auction and takes a row lock (SELECT ... FOR UPDATE), re-reading its state after the lock is
     * held. A plain locking query is not enough: if the entity is already in the persistence context
     * (open-session-in-view), Hibernate would keep the stale in-memory copy.
     */
    private Auction lockAuction(Long auctionId) {
        Auction auction = auctionRepository.findById(auctionId).orElse(null);
        if (auction != null) {
            entityManager.refresh(auction, LockModeType.PESSIMISTIC_WRITE);
        }
        return auction;
    }

    private static Map<String, Object> fail(Map<String, Object> result, String message) {
        result.put("success", false);
        result.put("message", message);
        return result;
    }

    /**
     * Demo-only (off by default, see app.demo.refresh-stale-auctions): re-opens LIVE auctions whose end
     * time has passed so seeded demo auctions stay visible. Never run this against real data.
     */
    @Transactional
    public void refreshStaleActiveAuctionWindows() {
        LocalDateTime now = LocalDateTime.now();
        for (Auction auction : auctionRepository.findLiveEndedBefore(now)) {
            if (auction.getStartTime() == null || auction.getStartTime().isAfter(now)) {
                auction.setStartTime(now.minusHours(1));
            }
            auction.setEndTime(now.plusDays(7));
            auction.setStatus(DISPLAY_LIVE);
        }
    }

    public void triggerAuctionStartNotification(Auction auction) {
        if (auction == null || auction.getProduct() == null) {
            return;
        }
        notifyAllNonArtisans("Auction Started!",
                "A new auction for " + auction.getProduct().getTitle() + " has just started!",
                "AUCTION_START", "/ProductDetailsAuction?id=" + auction.getId());
    }

    public void triggerAuctionEndNotifications(Auction auction) {
        if (auction == null || auction.getProduct() == null) {
            return;
        }
        String title = auction.getProduct().getTitle();
        if (auction.getHighestBidder() != null) {
            notificationService.sendNotification(auction.getHighestBidder(), "Auction Won!",
                    "Congratulations! You won the auction for " + title + " with a bid of "
                            + auction.getCurrentHighestBid().stripTrailingZeros().toPlainString()
                            + " BD. Add it to your cart from the auction page to complete the purchase.",
                    "AUCTION_WON", "/ProductDetailsAuction?id=" + auction.getId());
        }
        if (auction.getProduct().getArtisan() != null) {
            String artisanMsg = auction.getHighestBidder() != null
                    ? "Your auction for " + title + " has ended! Winner: " + auction.getHighestBidder().getName()
                            + " with a bid of " + auction.getCurrentHighestBid().stripTrailingZeros().toPlainString() + " BD."
                    : "Your auction for " + title + " has ended with no bids.";
            notificationService.sendNotification(auction.getProduct().getArtisan(), "Auction Ended", artisanMsg,
                    "AUCTION_ENDED", "/artisanAuction");
        }
        notifyAllNonArtisans("Auction Ended", "The auction for " + title + " has ended.", "AUCTION_ENDED",
                "/auctions");
    }

    /** Moves stored statuses along with the clock. Called by the scheduler, not by request handlers. */
    @Transactional
    public void syncStoredStatuses() {
        for (Auction auction : auctionRepository.findActiveAndUpcomingAuctions()) {
            String resolved = resolveDisplayStatus(auction);
            String stored = normalizeStatus(auction.getStatus());
            if (resolved.equals(stored)) {
                continue;
            }
            if (DISPLAY_LIVE.equals(resolved) && DISPLAY_UPCOMING.equals(stored)) {
                triggerAuctionStartNotification(auction);
            } else if (DISPLAY_ENDED.equals(resolved)
                    && (DISPLAY_LIVE.equals(stored) || DISPLAY_UPCOMING.equals(stored))) {
                triggerAuctionEndNotifications(auction);
            }
            auction.setStatus(resolved);
        }
    }

    private void notifyAllNonArtisans(String title, String message, String type, String link) {
        userRepository.findByRoleNotIgnoreCase(Roles.ARTISAN)
                .forEach(user -> notificationService.sendNotification(user, title, message, type, link));
    }

    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> buildAdminMonitorData(Long auctionId) {
        return auctionRepository.findByIdWithProductAndArtisan(auctionId).map(auction -> {
            String displayStatus = resolveDisplayStatus(auction);
            Product product = auction.getProduct();

            List<Bid> bids = bidRepository.findByAuctionIdWithCustomerOrderByBidTimeDesc(auctionId);
            long uniqueBidders = bidRepository.countDistinctBiddersByAuctionId(auctionId);

            BigDecimal lastBidDelta = BigDecimal.ZERO;
            if (bids.size() >= 2) {
                lastBidDelta = bids.get(0).getBidAmount().subtract(bids.get(1).getBidAmount());
            } else if (bids.size() == 1) {
                lastBidDelta = bids.get(0).getBidAmount().subtract(auction.getStartingBid());
            }

            long secondsRemaining = 0;
            if (auction.getEndTime() != null && DISPLAY_LIVE.equals(displayStatus)) {
                secondsRemaining = Math.max(0, ChronoUnit.SECONDS.between(LocalDateTime.now(), auction.getEndTime()));
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("auctionId", auction.getId());
            data.put("displayStatus", displayStatus);
            data.put("startingBid", auction.getStartingBid());
            data.put("currentHighestBid", auction.getCurrentHighestBid());
            data.put("totalBids", bids.size());
            data.put("uniqueBidders", uniqueBidders);
            data.put("lastBidDelta", lastBidDelta);
            data.put("secondsRemaining", secondsRemaining);
            data.put("startTime", auction.getStartTime() != null ? auction.getStartTime().toString() : null);
            data.put("endTime", auction.getEndTime() != null ? auction.getEndTime().toString() : null);
            data.put("endTimeLabel", formatEndTimeLabel(auction.getEndTime()));
            data.put("highestBidderName", auction.getHighestBidderName());

            if (product != null) {
                data.put("productTitle", product.getTitle());
                data.put("productId", product.getId());
                data.put("productSku", "PRD-" + product.getId());
                data.put("productDescription", product.getDescription());
                data.put("productImageUrl", product.getImageUrl());
                data.put("productCategory", product.getCategory());
                data.put("artisanName", product.getArtisanName());

                if (product.getArtisan() != null) {
                    Long artisanId = product.getArtisan().getUserId();
                    List<Review> artisanReviews = reviewRepository.findByProductArtisanUserId(artisanId);
                    double avgRating = artisanReviews.stream()
                            .filter(r -> r.getStatus() == null || !"rejected".equalsIgnoreCase(r.getStatus()))
                            .mapToInt(Review::getRating)
                            .average()
                            .orElse(0);
                    data.put("artisanAuctionCount", auctionRepository.countByProductArtisanUserId(artisanId));
                    data.put("artisanAvgRating", Math.round(avgRating * 10.0) / 10.0);
                    data.put("artisanReviewCount", artisanReviews.size());
                    data.put("artisanProfilePicture", product.getArtisan().getProfilePicture());
                }
            }

            List<Map<String, Object>> bidHistory = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();
            for (Bid bid : bids) {
                Map<String, Object> row = new LinkedHashMap<>();
                String bidderName = bid.getCustomer() != null && bid.getCustomer().getName() != null
                        ? bid.getCustomer().getName()
                        : "Anonymous";
                row.put("bidderName", bidderName);
                row.put("bidderInitial", bidderName.isEmpty() ? "?" : bidderName.substring(0, 1).toUpperCase());
                row.put("bidderImage", bid.getCustomer() != null ? bid.getCustomer().getProfilePicture() : null);
                row.put("bidAmount", bid.getBidAmount());
                row.put("bidTime", bid.getBidTime() != null ? bid.getBidTime().toString() : null);
                row.put("timeAgo", formatTimeAgo(bid.getBidTime(), now));
                bidHistory.add(row);
            }
            data.put("bidHistory", bidHistory);
            return data;
        });
    }

    private String formatEndTimeLabel(LocalDateTime endTime) {
        if (endTime == null) {
            return "-";
        }
        return "Ends " + endTime.format(DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' h:mm a"));
    }

    private String formatTimeAgo(LocalDateTime bidTime, LocalDateTime now) {
        if (bidTime == null) {
            return "-";
        }
        long minutes = ChronoUnit.MINUTES.between(bidTime, now);
        if (minutes < 1) {
            return "Just now";
        }
        if (minutes < 60) {
            return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        }
        long hours = ChronoUnit.HOURS.between(bidTime, now);
        if (hours < 24) {
            return hours + (hours == 1 ? " hour ago" : " hours ago");
        }
        long days = ChronoUnit.DAYS.between(bidTime, now);
        return days + (days == 1 ? " day ago" : " days ago");
    }

    @Transactional
    public Map<String, Object> extendAuction(Long auctionId, int minutes) {
        Map<String, Object> result = new HashMap<>();
        if (minutes < 1 || minutes > MAX_ADMIN_EXTENSION_MINUTES) {
            return fail(result, "Extension must be between 1 minute and 7 days.");
        }
        Auction auction = lockAuction(auctionId);
        if (auction == null) {
            return fail(result, "Auction not found.");
        }
        String display = resolveDisplayStatus(auction);
        if (DISPLAY_ENDED.equals(display) || STATUS_CANCELLED.equals(display)) {
            return fail(result, "Cannot extend an auction that has already ended.");
        }
        LocalDateTime newEnd = (auction.getEndTime() != null ? auction.getEndTime() : LocalDateTime.now())
                .plusMinutes(minutes);
        auction.setEndTime(newEnd);

        if (auction.getHighestBidder() != null) {
            notificationService.sendNotification(auction.getHighestBidder(), "Auction Extended",
                    "The auction for " + (auction.getProduct() != null ? auction.getProduct().getTitle() : "an item")
                            + " has been extended by " + minutes + " minutes.",
                    "AUCTION_EXTENDED", "/ProductDetailsAuction?id=" + auction.getId());
        }

        result.put("success", true);
        result.put("message", "Auction extended by " + minutes + " minutes. The highest bidder has been notified.");
        result.put("newEndTime", newEnd.toString());
        return result;
    }

    @Transactional
    public Map<String, Object> cancelAuction(Long auctionId) {
        Map<String, Object> result = new HashMap<>();
        Auction auction = lockAuction(auctionId);
        if (auction == null) {
            return fail(result, "Auction not found.");
        }
        if (STATUS_CANCELLED.equalsIgnoreCase(auction.getStatus())) {
            return fail(result, "Auction is already cancelled.");
        }

        auction.setStatus(STATUS_CANCELLED);

        if (auction.getHighestBidder() != null) {
            notificationService.sendNotification(auction.getHighestBidder(), "Auction Cancelled",
                    "The auction for " + (auction.getProduct() != null ? auction.getProduct().getTitle() : "an item")
                            + " has been cancelled by the admin.",
                    "AUCTION_CANCELLED", "/auctions");
        }
        if (auction.getProduct() != null && auction.getProduct().getArtisan() != null) {
            notificationService.sendNotification(auction.getProduct().getArtisan(), "Your Auction Was Cancelled",
                    "Your auction for " + auction.getProduct().getTitle() + " has been cancelled by an administrator.",
                    "AUCTION_CANCELLED", "/artisanAuction");
        }

        result.put("success", true);
        result.put("message", "Auction cancelled. The highest bidder and the artisan have been notified.");
        return result;
    }

    @Transactional
    public Map<String, Object> flagAuction(Long auctionId, String reason) {
        Map<String, Object> result = new HashMap<>();
        Auction auction = auctionRepository.findById(auctionId).orElse(null);
        if (auction == null) {
            return fail(result, "Auction not found.");
        }
        auction.setFlagged(true);
        auction.setFlagReason(reason != null && !reason.isBlank() ? reason.trim() : "Flagged for admin review");

        result.put("success", true);
        result.put("message", "Auction flagged for review.");
        result.put("flagReason", auction.getFlagReason());
        return result;
    }
}
