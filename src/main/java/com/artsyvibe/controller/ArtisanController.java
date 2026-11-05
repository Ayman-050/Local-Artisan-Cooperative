package com.artsyvibe.controller;

import com.artsyvibe.model.Artisan;
import com.artsyvibe.model.ArtisanOrderSummary;
import com.artsyvibe.model.OrderItem;
import com.artsyvibe.model.OrderStatus;
import com.artsyvibe.repository.AuctionRepository;
import com.artsyvibe.repository.OrderItemRepository;
import com.artsyvibe.repository.OrderRepository;
import com.artsyvibe.repository.ProductRepository;
import com.artsyvibe.security.CurrentUser;
import com.artsyvibe.service.ArtisanStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * Artisan back-office pages. They always show the signed-in artisan's own data. The {@code ?id=} parameter
 * the front end still appends is ignored (it used to select whose data to show, for anyone).
 */
@Controller
@RequiredArgsConstructor
public class ArtisanController {

    private final ProductRepository productRepository;
    private final AuctionRepository auctionRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;
    private final ArtisanStatsService stats;
    private final CurrentUser currentUser;

    /** Loads the artisan and fills defaults every artisan template expects. */
    private Artisan prepare(Model model) {
        Artisan artisan = currentUser.requireArtisan();
        model.addAttribute("artisan", artisan);
        model.addAttribute("productsCount", 0L);
        model.addAttribute("activeAuctions", 0L);
        model.addAttribute("pendingOrders", 0L);
        model.addAttribute("totalRevenue", BigDecimal.ZERO);
        model.addAttribute("recentOrders", Collections.emptyList());
        model.addAttribute("products", Collections.emptyList());
        model.addAttribute("orders", Collections.emptyList());
        model.addAttribute("auctions", Collections.emptyList());
        model.addAttribute("ordersCount", 0L);
        model.addAttribute("revenuePercentage", 0.0);
        model.addAttribute("ordersPercentage", 0.0);
        model.addAttribute("weeklyRevenue", Collections.nCopies(8, BigDecimal.ZERO));
        model.addAttribute("monthlyRevenue", Collections.nCopies(6, BigDecimal.ZERO));
        model.addAttribute("bestSellers", Collections.emptyList());
        model.addAttribute("averageRating", 0.0);
        model.addAttribute("totalReviewsCount", 0);
        model.addAttribute("starPercent", List.of(0, 0, 0, 0, 0));
        model.addAttribute("starDistribution", new int[5]);
        return artisan;
    }

    private static long pendingCount(List<ArtisanOrderSummary> summaries) {
        return summaries.stream().filter(s -> OrderStatus.PENDING.equalsIgnoreCase(s.status())).count();
    }

    @GetMapping("/artisanDashboard")
    public String dashboard(Model model) {
        Long id = prepare(model).getUserId();
        List<OrderItem> items = stats.soldItems(id);
        List<ArtisanOrderSummary> summaries = stats.orderSummaries(items);
        model.addAttribute("productsCount", productRepository.countByArtisanUserId(id));
        model.addAttribute("activeAuctions", auctionRepository.countByProductArtisanUserIdAndStatus(id, "LIVE"));
        model.addAttribute("pendingOrders", pendingCount(summaries));
        model.addAttribute("totalRevenue", stats.revenue(items));
        model.addAttribute("recentOrders", summaries);
        return "artisanDashboard";
    }

    @GetMapping("/artisanProducts")
    public String products(Model model) {
        Long id = prepare(model).getUserId();
        model.addAttribute("products", productRepository.findByArtisanUserIdAndIsAuctionItem(id, false));
        model.addAttribute("productsCount", productRepository.countByArtisanUserIdAndIsAuctionItem(id, false));
        return "artisanProducts";
    }

    @GetMapping("/artisanAuction")
    public String auctions(Model model) {
        Long id = prepare(model).getUserId();
        model.addAttribute("auctions", auctionRepository.findByProductArtisanUserId(id));
        // Non-auction products populate the "Add Auction" dropdown.
        model.addAttribute("products", productRepository.findByArtisanUserIdAndIsAuctionItem(id, false));
        return "artisanAuction";
    }

    @GetMapping("/artisanOrders")
    public String orders(Model model) {
        Long id = prepare(model).getUserId();
        List<ArtisanOrderSummary> summaries = stats.orderSummaries(stats.soldItems(id));
        model.addAttribute("orders", summaries);
        model.addAttribute("ordersCount", (long) summaries.size());
        return "artisanOrders";
    }

    @GetMapping("/artisanAnalytics")
    public String analytics(Model model) {
        Long id = prepare(model).getUserId();
        LocalDate today = LocalDate.now();
        List<OrderItem> items = stats.soldItems(id);
        List<ArtisanOrderSummary> summaries = stats.orderSummaries(items);

        model.addAttribute("productsCount", productRepository.countByArtisanUserId(id));
        model.addAttribute("activeAuctions", auctionRepository.countByProductArtisanUserIdAndStatus(id, "LIVE"));
        model.addAttribute("pendingOrders", pendingCount(summaries));
        model.addAttribute("totalRevenue", stats.revenue(items));
        model.addAttribute("ordersCount", (long) summaries.size());

        model.addAttribute("weeklyRevenue", stats.weeklyRevenue(items, today));
        List<BigDecimal> monthly = stats.monthlyRevenue(items, today);
        model.addAttribute("monthlyRevenue", monthly);
        model.addAttribute("revenuePercentage", ArtisanStatsService.percentChange(monthly.get(5), monthly.get(4)));

        LocalDate thisMonthStart = today.withDayOfMonth(1);
        LocalDate lastMonthStart = thisMonthStart.minusMonths(1);
        long ordersThisMonth = summaries.stream()
                .filter(s -> s.order().getDate() != null && !s.order().getDate().isBefore(thisMonthStart)).count();
        long ordersLastMonth = summaries.stream()
                .filter(s -> s.order().getDate() != null && !s.order().getDate().isBefore(lastMonthStart)
                        && s.order().getDate().isBefore(thisMonthStart)).count();
        model.addAttribute("ordersPercentage", ArtisanStatsService.percentChange(ordersThisMonth, ordersLastMonth));

        model.addAttribute("bestSellers", stats.bestSellers(items, 5));

        ArtisanStatsService.ReviewSummary reviews = stats.reviewSummary(id);
        model.addAttribute("averageRating", reviews.average());
        model.addAttribute("totalReviewsCount", reviews.count());
        model.addAttribute("starPercent", reviews.percents());
        model.addAttribute("starDistribution", reviews.distribution());
        return "artisanAnalytics";
    }

    @GetMapping("/artisanSettings")
    public String settings(Model model) {
        prepare(model);
        return "artisanSettings";
    }

    /** An order is only shown if it contains this artisan's products, and only their lines are listed. */
    @GetMapping("/artisanOrderDetail")
    public String ordersDetail(@RequestParam(required = false) Long orderId, Model model) {
        Long id = prepare(model).getUserId();
        if (orderId != null && orderItemRepository.existsByOrderAndArtisan(orderId, id)) {
            orderRepository.findById(orderId).ifPresent(order -> {
                List<OrderItem> artisanItems = orderItemRepository.findByOrderIdWithProduct(orderId).stream()
                        .filter(item -> item.getProduct().getArtisan() != null
                                && item.getProduct().getArtisan().getUserId().equals(id))
                        .toList();
                model.addAttribute("order", order);
                model.addAttribute("orderItems", artisanItems);
                model.addAttribute("artisanSubtotal", artisanItems.stream()
                        .map(ArtisanStatsService::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add));
                // The page shows and edits the status of this artisan's items, not the whole order's.
                model.addAttribute("artisanStatus", OrderStatus.aggregate(
                        artisanItems.stream().map(OrderItem::getFulfilmentStatus).toList()));
            });
        }
        return "artisanOrderDetail";
    }

    @GetMapping("/artisanNotification")
    public String notifications(Model model) {
        prepare(model);
        return "artisanNotification";
    }
}
