package com.example.demo.service;

import com.example.demo.model.Auction;
import com.example.demo.model.Bid;
import com.example.demo.model.Customer;
import com.example.demo.model.Product;
import com.example.demo.model.Review;
import com.example.demo.model.User;
import com.example.demo.repository.AuctionRepository;
import com.example.demo.repository.BidRepository;
import com.example.demo.repository.ReviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
public class AuctionService {

    public static final String DISPLAY_LIVE = "LIVE";
    public static final String DISPLAY_UPCOMING = "UPCOMING";
    public static final String DISPLAY_ENDED = "ENDED";

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private com.example.demo.repository.CustomerRepository customerRepository;

    @Autowired
    private com.example.demo.repository.UserRepository userRepository;

    @Autowired
    private BidRepository bidRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    public String resolveDisplayStatus(Auction auction) {
        if (auction == null) return DISPLAY_ENDED;
        LocalDateTime now = LocalDateTime.now();
        String stored = normalizeStatus(auction.getStatus());

        if ("CANCELLED".equals(stored) || "DELETED".equals(stored)) {
            return "CANCELLED";
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
        if (DISPLAY_UPCOMING.equals(stored) && auction.getStartTime() != null && now.isBefore(auction.getStartTime())) {
            return DISPLAY_UPCOMING;
        }
        return DISPLAY_LIVE;
    }

    public static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return "";
        String upper = status.trim().toUpperCase();
        if ("ACTIVE".equals(upper)) return DISPLAY_LIVE;
        return upper;
    }

    public Optional<Auction> findById(Long id) {
        return auctionRepository.findById(id);
    }

    public Map<String, Object> toDetailMap(Auction auction) {
        Map<String, Object> map = new HashMap<>();
        String displayStatus = resolveDisplayStatus(auction);
        map.put("id", auction.getId());
        map.put("displayStatus", displayStatus);
        map.put("startingBid", (int) Math.floor(auction.getStartingBid()));
        map.put("currentHighestBid", (int) Math.floor(auction.getCurrentHighestBid()));
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

    public int computeMinNextBid(Auction auction) {
        int current = (int) Math.floor(auction.getCurrentHighestBid());
        int starting = (int) Math.floor(auction.getStartingBid());
        if (current < starting) {
            return starting;
        }
        return current + 1;
    }

    public Map<String, Object> placeBid(Long auctionId, int bidAmount, String bidderName, Long bidderId) {
        Map<String, Object> result = new HashMap<>();
        Auction auction = auctionRepository.findById(auctionId).orElse(null);
        if (auction == null) {
            result.put("success", false);
            result.put("message", "Auction not found.");
            return result;
        }

        Customer newBidder = bidderId != null ? customerRepository.findById(bidderId).orElse(null) : null;
        if (newBidder == null && bidderId != null) {
            result.put("success", false);
            result.put("message", "User not found.");
            return result;
        }

        String displayStatus = resolveDisplayStatus(auction);
        if (DISPLAY_ENDED.equals(displayStatus)) {
            result.put("success", false);
            result.put("message", "Auction has already ended.");
            return result;
        }
        if (DISPLAY_UPCOMING.equals(displayStatus)) {
            result.put("success", false);
            result.put("message", "This auction has not started yet.");
            return result;
        }

        if (bidderName == null || bidderName.isBlank()) {
            result.put("success", false);
            result.put("message", "Please enter your name.");
            return result;
        }

        int minBid = computeMinNextBid(auction);
        if (bidAmount < minBid) {
            result.put("success", false);
            result.put("message", "Your bid must be at least " + minBid + " BD (current bid + 1 BD minimum).");
            return result;
        }

        // Notify previous highest bidder
        if (auction.getHighestBidder() != null && !auction.getHighestBidder().getUserId().equals(bidderId)) {
            notificationService.sendNotification(
                auction.getHighestBidder(),
                "Outbid!",
                "Someone has placed a higher bid of " + bidAmount + " BD on " + auction.getProduct().getTitle(),
                "BID_OUTBID",
                "/auctions" // Link to auctions page
            );
        }

        boolean extended = false;
        long secondsRemaining = ChronoUnit.SECONDS.between(LocalDateTime.now(), auction.getEndTime());
        if (secondsRemaining <= 30 && secondsRemaining >= 0) {
            auction.setEndTime(auction.getEndTime().plusMinutes(2));
            extended = true;
        }

        auction.setCurrentHighestBid(bidAmount);
        auction.setHighestBidderName(bidderName.trim());
        auction.setHighestBidder(newBidder);
        auction.setStatus(DISPLAY_LIVE);
        auctionRepository.save(auction);

        // Record the bid in Bid History
        Bid newBid = new Bid();
        newBid.setAuction(auction);
        newBid.setBidAmount(bidAmount);
        newBid.setBidTime(LocalDateTime.now());
        newBid.setCustomer(newBidder);
        bidRepository.save(newBid);

        result.put("success", true);
        result.put("message", extended
                ? "Bid placed! Anti-snipe rule applied — auction extended by 2 minutes."
                : "Bid placed successfully!");
        result.put("extended", extended);
        result.put("auction", toDetailMap(auction));
        return result;
    }

    /** Keeps demo auctions visible on Replit when seed dates are in the past. */
    public void refreshStaleActiveAuctionWindows() {
        LocalDateTime now = LocalDateTime.now();
        for (Auction auction : auctionRepository.findAll()) {
            String stored = normalizeStatus(auction.getStatus());
            if (!DISPLAY_LIVE.equals(stored) && !"ACTIVE".equalsIgnoreCase(auction.getStatus())) {
                continue;
            }
            if (auction.getEndTime() == null || !auction.getEndTime().isBefore(now)) {
                continue;
            }
            if (auction.getStartTime() == null || auction.getStartTime().isAfter(now)) {
                auction.setStartTime(now.minusHours(1));
            }
            auction.setEndTime(now.plusDays(7));
            auction.setStatus(DISPLAY_LIVE);
            auctionRepository.save(auction);
        }
    }

    public void triggerAuctionStartNotification(Auction auction) {
        if (auction == null || auction.getProduct() == null) return;
        notifyAllUsers(
            "Auction Started!",
            "A new auction for " + auction.getProduct().getTitle() + " has just started!",
            "AUCTION_START",
            "/auctions"
        );
    }

    public void triggerAuctionEndNotifications(Auction auction) {
        if (auction == null || auction.getProduct() == null) return;

        // 1. Send "Auction Won" notification to the winner
        if (auction.getHighestBidder() != null) {
            notificationService.sendNotification(
                auction.getHighestBidder(),
                "Auction Won!",
                "Congratulations! You won the auction for " + auction.getProduct().getTitle() + " with a bid of " + auction.getCurrentHighestBid() + " BD",
                "AUCTION_WON",
                "/orders"
            );
        }

        // 2. Send "Auction Ended" notification to the artisan (seller)
        if (auction.getProduct().getArtisan() != null) {
            String artisanMsg = auction.getHighestBidder() != null 
                ? "Your auction for " + auction.getProduct().getTitle() + " has ended! Winner: " + auction.getHighestBidder().getName() + " with a bid of " + auction.getCurrentHighestBid() + " BD."
                : "Your auction for " + auction.getProduct().getTitle() + " has ended with no bids.";
            notificationService.sendNotification(
                auction.getProduct().getArtisan(),
                "Auction Ended",
                artisanMsg,
                "AUCTION_ENDED",
                "/artisanAuction"
            );
        }

        // 3. Send general "Auction Ended" notification to all other users
        notifyAllUsers(
            "Auction Ended",
            "The auction for " + auction.getProduct().getTitle() + " has ended.",
            "AUCTION_ENDED",
            "/auctions"
        );
    }

    public void syncStoredStatuses() {
        LocalDateTime now = LocalDateTime.now();
        for (Auction auction : auctionRepository.findActiveAndUpcomingAuctions()) {
            String resolved = resolveDisplayStatus(auction);
            String normalized = normalizeStatus(auction.getStatus());

            if (!resolved.equals(normalized)) {
                // Trigger notifications based on status change
                if (DISPLAY_LIVE.equals(resolved) && DISPLAY_UPCOMING.equals(normalized)) {
                    triggerAuctionStartNotification(auction);
                } else if (DISPLAY_ENDED.equals(resolved) && (DISPLAY_LIVE.equals(normalized) || DISPLAY_UPCOMING.equals(normalized))) {
                    triggerAuctionEndNotifications(auction);
                }

                auction.setStatus(resolved);
                auctionRepository.save(auction);
            }
        }
    }

    private void notifyAllUsers(String title, String message, String type, String link) {
        userRepository.findAll().forEach(user -> {
            if ("ARTISAN".equalsIgnoreCase(user.getRole())) {
                return;
            }
            notificationService.sendNotification(user, title, message, type, link);
        });
    }

    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> buildAdminMonitorData(Long auctionId) {
        syncStoredStatuses();
        return auctionRepository.findByIdWithProductAndArtisan(auctionId).map(auction -> {
            String displayStatus = resolveDisplayStatus(auction);
            Product product = auction.getProduct();

            List<Bid> bids = bidRepository.findByAuctionIdWithCustomerOrderByBidTimeDesc(auctionId);
            long totalBids = bids.size();
            long uniqueBidders = bidRepository.countDistinctBiddersByAuctionId(auctionId);

            double lastBidDelta = 0;
            if (bids.size() >= 2) {
                lastBidDelta = bids.get(0).getBidAmount() - bids.get(1).getBidAmount();
            } else if (bids.size() == 1) {
                lastBidDelta = bids.get(0).getBidAmount() - auction.getStartingBid();
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
            data.put("totalBids", totalBids);
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
                    long artisanAuctionCount = auctionRepository.countByProductArtisanUserId(artisanId);
                    List<Review> artisanReviews = reviewRepository.findByProductArtisanUserId(artisanId);
                    double avgRating = artisanReviews.stream()
                            .filter(r -> r.getStatus() == null || !"rejected".equalsIgnoreCase(r.getStatus()))
                            .mapToInt(Review::getRating)
                            .average()
                            .orElse(0);
                    data.put("artisanAuctionCount", artisanAuctionCount);
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
            return "—";
        }
        return "Ends " + endTime.format(DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' h:mm a"));
    }

    private String formatTimeAgo(LocalDateTime bidTime, LocalDateTime now) {
        if (bidTime == null) {
            return "—";
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
        Auction auction = auctionRepository.findById(auctionId).orElse(null);
        if (auction == null) {
            result.put("success", false);
            result.put("message", "Auction not found.");
            return result;
        }
        if (DISPLAY_ENDED.equals(normalizeStatus(auction.getStatus()))) {
            result.put("success", false);
            result.put("message", "Cannot extend an auction that has already ended.");
            return result;
        }
        LocalDateTime newEnd = (auction.getEndTime() != null ? auction.getEndTime() : LocalDateTime.now())
                .plusMinutes(minutes);
        auction.setEndTime(newEnd);
        auction.setStatus(DISPLAY_LIVE);
        auctionRepository.save(auction);

        // Notify the highest bidder if present
        if (auction.getHighestBidder() != null) {
            notificationService.sendNotification(
                auction.getHighestBidder(),
                "Auction Extended",
                "The auction for " + (auction.getProduct() != null ? auction.getProduct().getTitle() : "an item")
                    + " has been extended by " + minutes + " minutes.",
                "AUCTION_EXTENDED",
                "/auctions"
            );
        }

        result.put("success", true);
        result.put("message", "Auction extended by " + minutes + " minutes. All bidders have been notified.");
        result.put("newEndTime", newEnd.toString());
        return result;
    }

    @Transactional
    public Map<String, Object> cancelAuction(Long auctionId) {
        Map<String, Object> result = new HashMap<>();
        Auction auction = auctionRepository.findById(auctionId).orElse(null);
        if (auction == null) {
            result.put("success", false);
            result.put("message", "Auction not found.");
            return result;
        }
        if ("CANCELLED".equalsIgnoreCase(auction.getStatus())) {
            result.put("success", false);
            result.put("message", "Auction is already cancelled.");
            return result;
        }

        auction.setStatus("CANCELLED");
        auctionRepository.save(auction);

        // Notify highest bidder
        if (auction.getHighestBidder() != null) {
            notificationService.sendNotification(
                auction.getHighestBidder(),
                "Auction Cancelled",
                "The auction for " + (auction.getProduct() != null ? auction.getProduct().getTitle() : "an item")
                    + " has been cancelled by the admin.",
                "AUCTION_CANCELLED",
                "/auctions"
            );
        }

        // Notify artisan
        if (auction.getProduct() != null && auction.getProduct().getArtisan() != null) {
            notificationService.sendNotification(
                auction.getProduct().getArtisan(),
                "Your Auction Was Cancelled",
                "Your auction for " + auction.getProduct().getTitle() + " has been cancelled by an administrator.",
                "AUCTION_CANCELLED",
                "/artisanAuction"
            );
        }

        result.put("success", true);
        result.put("message", "Auction cancelled. All bidders and the artisan have been notified.");
        return result;
    }

    @Transactional
    public Map<String, Object> flagAuction(Long auctionId, String reason) {
        Map<String, Object> result = new HashMap<>();
        Auction auction = auctionRepository.findById(auctionId).orElse(null);
        if (auction == null) {
            result.put("success", false);
            result.put("message", "Auction not found.");
            return result;
        }

        auction.setFlagged(true);
        auction.setFlagReason(reason != null && !reason.isBlank() ? reason.trim() : "Flagged for admin review");
        auctionRepository.save(auction);

        result.put("success", true);
        result.put("message", "Auction flagged for review. Investigation team has been notified.");
        result.put("flagReason", auction.getFlagReason());
        return result;
    }
}
