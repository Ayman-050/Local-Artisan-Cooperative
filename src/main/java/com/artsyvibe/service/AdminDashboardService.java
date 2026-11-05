package com.artsyvibe.service;

import com.artsyvibe.model.Artisan;
import com.artsyvibe.model.Auction;
import com.artsyvibe.model.Order;
import com.artsyvibe.model.OrderItem;
import com.artsyvibe.model.OrderStatus;
import com.artsyvibe.model.RefundStatus;
import com.artsyvibe.model.Review;
import com.artsyvibe.model.UserStatus;
import com.artsyvibe.repository.ArtisanRepository;
import com.artsyvibe.repository.AuctionRepository;
import com.artsyvibe.repository.OrderItemRepository;
import com.artsyvibe.repository.OrderRepository;
import com.artsyvibe.repository.ProductRepository;
import com.artsyvibe.repository.ReviewRepository;
import com.artsyvibe.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Figures for the admin dashboard, computed with count/sum queries instead of loading whole tables. */
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final UserRepository userRepository;
    private final ArtisanRepository artisanRepository;
    private final ProductRepository productRepository;
    private final AuctionRepository auctionRepository;
    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    /** Inclusive [start, end] of the month containing {@code day}. */
    private record Month(LocalDate start, LocalDate end) {
        static Month of(LocalDate day) {
            return new Month(day.withDayOfMonth(1), day.withDayOfMonth(day.lengthOfMonth()));
        }

        LocalDateTime startTime() {
            return start.atStartOfDay();
        }

        LocalDateTime endTime() {
            return end.plusDays(1).atStartOfDay().minusNanos(1);
        }

    }

    @Transactional(readOnly = true)
    public Map<String, Object> build(LocalDate today) {
        Month thisMonth = Month.of(today);
        Month lastMonth = Month.of(today.minusMonths(1));
        Map<String, Object> m = new LinkedHashMap<>();

        m.put("totalUsers", userRepository.count());
        m.put("totalArtisans", artisanRepository.count());

        long productsNow = productRepository.countByAddingDateBetween(thisMonth.start(), thisMonth.end());
        long productsPrev = productRepository.countByAddingDateBetween(lastMonth.start(), lastMonth.end());
        m.put("activeProducts", productsNow);
        m.put("productGrowth", ArtisanStatsService.percentChange(productsNow, productsPrev));

        long auctionsNow = auctionRepository.countByStartTimeBetween(thisMonth.startTime(), thisMonth.endTime());
        long auctionsPrev = auctionRepository.countByStartTimeBetween(lastMonth.startTime(), lastMonth.endTime());
        m.put("openAuctions", auctionsNow);
        m.put("auctionGrowth", ArtisanStatsService.percentChange(auctionsNow, auctionsPrev));

        long reviewsNow = reviewRepository.countByDateBetween(thisMonth.start(), thisMonth.end());
        long reviewsPrev = reviewRepository.countByDateBetween(lastMonth.start(), lastMonth.end());
        m.put("totalReviews", reviewsNow);
        m.put("reviewGrowth", ArtisanStatsService.percentChange(reviewsNow, reviewsPrev));

        m.put("userGrowth", ArtisanStatsService.percentChange(
                userRepository.countByJoinDateBetween(thisMonth.startTime(), thisMonth.endTime()),
                userRepository.countByJoinDateBetween(lastMonth.startTime(), lastMonth.endTime())));
        m.put("artisanGrowth", ArtisanStatsService.percentChange(
                artisanRepository.countByJoinDateBetween(thisMonth.startTime(), thisMonth.endTime()),
                artisanRepository.countByJoinDateBetween(lastMonth.startTime(), lastMonth.endTime())));

        m.put("pendingRefundsCount",
                orderItemRepository.countByRefundRequestedTrueAndAdminRefundStatusIgnoreCase(RefundStatus.PENDING));
        m.put("pendingRefundsGrowth", ArtisanStatsService.percentChange(
                orderItemRepository.countRefundRequestsByOrderDate(thisMonth.start(), thisMonth.end()),
                orderItemRepository.countRefundRequestsByOrderDate(lastMonth.start(), lastMonth.end())));

        m.put("pendingArtisansCount", (long) artisanRepository
                .findByStatusIn(List.of(UserStatus.PENDING_APPROVAL, UserStatus.PENDING)).size());
        m.put("flaggedReviewsCount", reviewRepository.countByAutoFlaggedTrue());
        m.put("recentActivities", recentActivities(orderItemRepository.findTop5ByRefundRequestedTrueOrderByOrderItemIdDesc()));

        // Orders per week, last 6 weeks (index 5 = the 7 days ending today).
        int[] ordersPerWeek = new int[6];
        for (int i = 0; i < 6; i++) {
            LocalDate end = today.minusDays(7L * (5 - i));
            ordersPerWeek[i] = (int) orderRepository.countPlacedBetween(end.minusDays(6), end);
        }
        m.put("ordersPerWeek", ordersPerWeek);

        // Revenue per month, last 6 months.
        BigDecimal[] revenuePerMonth = new BigDecimal[6];
        String[] monthLabels = new String[6];
        for (int i = 0; i < 6; i++) {
            LocalDate monthDay = today.minusMonths(5 - i);
            Month month = Month.of(monthDay);
            monthLabels[i] = monthDay.getMonth().name().substring(0, 3);
            revenuePerMonth[i] = orderRepository.sumPlacedBetween(month.start(), month.end());
        }
        m.put("revenuePerMonth", revenuePerMonth);
        m.put("monthLabels", monthLabels);
        return m;
    }

    /** The 5 most recent events across artisans, orders, refunds, auctions and flagged reviews. */
    private List<RecentActivity> recentActivities(List<OrderItem> refunds) {
        List<RecentActivity> activities = new ArrayList<>();

        for (Artisan artisan : artisanRepository.findTop5ByJoinDateNotNullOrderByJoinDateDesc()) {
            boolean pending = UserStatus.isPending(artisan.getStatus());
            activities.add(new RecentActivity("artisan_reg",
                    "New artisan registration: <span class=\"font-semibold\">" + esc(artisan.getName()) + "</span> "
                            + (pending ? "applied to join" : "joined") + " the platform",
                    artisan.getJoinDate(), "fa-user-plus text-green-600", "bg-green-50", "/adminArtisanApproval"));
        }
        for (Order order : orderRepository.findTop5ByStatusNotIgnoreCaseAndDateNotNullOrderByDateDescOrderIdDesc(OrderStatus.CART)) {
            boolean large = order.getTotalAmount().compareTo(BigDecimal.valueOf(100)) >= 0;
            activities.add(new RecentActivity("order",
                    (large ? "Large order placed: " : "New order placed: ")
                            + "Order <span class=\"font-mono font-semibold\">#ORD-" + order.getOrderId() + "</span> worth "
                            + String.format("%.3f BD", order.getTotalAmount()),
                    order.getDate().atStartOfDay(), "fa-shopping-bag text-blue-600", "bg-blue-50",
                    "/adminOrderDetail/" + order.getOrderId()));
        }
        refunds.stream()
                .filter(i -> i.getOrder() != null && i.getOrder().getDate() != null)
                .sorted((a, b) -> b.getOrder().getDate().compareTo(a.getOrder().getDate()))
                .limit(5)
                .forEach(item -> activities.add(new RecentActivity("refund",
                        "Refund request: Customer requested refund for order <span class=\"font-mono font-semibold\">#ORD-"
                                + item.getOrder().getOrderId() + "</span>",
                        item.getOrder().getDate().atStartOfDay(), "fa-exclamation-triangle text-red-600", "bg-red-50",
                        "/adminRefund")));
        for (Auction auction : auctionRepository.findTop5ByStatusIgnoreCaseAndEndTimeNotNullOrderByEndTimeDesc(
                AuctionService.DISPLAY_ENDED)) {
            String title = auction.getProduct() != null ? auction.getProduct().getTitle() : "Product";
            activities.add(new RecentActivity("auction",
                    "Auction completed: <span class=\"font-semibold\">" + esc(title) + "</span> sold for "
                            + String.format("%.3f BD", auction.getCurrentHighestBid()),
                    auction.getEndTime(), "fa-gavel text-purple-600", "bg-purple-50", "/adminAuction"));
        }
        for (Review review : reviewRepository.findTop5ByAutoFlaggedTrueAndDateNotNullOrderByDateDesc()) {
            String title = review.getProduct() != null ? review.getProduct().getTitle() : "Product";
            activities.add(new RecentActivity("review_flagged",
                    "Review flagged: Product review for <span class=\"font-semibold\">" + esc(title)
                            + "</span> reported as inappropriate",
                    review.getDate().atStartOfDay(), "fa-flag text-orange-600", "bg-orange-50", "/adminReview"));
        }
        return activities.stream().sorted().limit(5).toList();
    }

    /** Activity titles are rendered with th:utext (they contain markup), so user text must be escaped. */
    private static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s);
    }

    /** One line of the "Recent activity" feed. {@code title} is trusted HTML (user data already escaped). */
    public static class RecentActivity implements Comparable<RecentActivity> {
        private final String type;
        private final String title;
        private final String timeAgo;
        private final LocalDateTime dateTime;
        private final String iconClass;
        private final String bgClass;
        private final String link;

        public RecentActivity(String type, String title, LocalDateTime dateTime, String iconClass, String bgClass,
                String link) {
            this.type = type;
            this.title = title;
            this.dateTime = dateTime;
            this.iconClass = iconClass;
            this.bgClass = bgClass;
            this.link = link;
            this.timeAgo = formatTimeAgo(dateTime);
        }

        public String getType() {
            return type;
        }

        public String getTitle() {
            return title;
        }

        public String getTimeAgo() {
            return timeAgo;
        }

        public LocalDateTime getDateTime() {
            return dateTime;
        }

        public String getIconClass() {
            return iconClass;
        }

        public String getBgClass() {
            return bgClass;
        }

        public String getLink() {
            return link;
        }

        @Override
        public int compareTo(RecentActivity o) {
            if (this.dateTime == null && o.dateTime == null) {
                return 0;
            }
            if (this.dateTime == null) {
                return 1;
            }
            if (o.dateTime == null) {
                return -1;
            }
            return o.dateTime.compareTo(this.dateTime); // newest first
        }

        private static String formatTimeAgo(LocalDateTime dt) {
            if (dt == null) {
                return "Recent";
            }
            long seconds = ChronoUnit.SECONDS.between(dt, LocalDateTime.now());
            if (seconds < 0) {
                return "Just now";
            }
            if (seconds < 60) {
                return seconds + " seconds ago";
            }
            long minutes = seconds / 60;
            if (minutes < 60) {
                return minutes + " minutes ago";
            }
            long hours = minutes / 60;
            if (hours < 24) {
                return hours + " hours ago";
            }
            long days = hours / 24;
            if (days < 30) {
                return days + " days ago";
            }
            return (days / 30) + " months ago";
        }
    }
}
