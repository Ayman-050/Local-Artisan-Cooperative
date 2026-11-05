package com.artsyvibe.service;

import com.artsyvibe.model.ArtisanOrderSummary;
import com.artsyvibe.model.OrderItem;
import com.artsyvibe.model.OrderStatus;
import com.artsyvibe.model.Product;
import com.artsyvibe.model.RefundStatus;
import com.artsyvibe.model.Review;
import com.artsyvibe.repository.OrderItemRepository;
import com.artsyvibe.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Sales figures for one artisan. Revenue is what customers actually paid for the artisan's items
 * (order-line price × quantity, including won auction lots once they are bought), minus items whose
 * refund an admin approved.
 */
@Service
@RequiredArgsConstructor
public class ArtisanStatsService {

    private final OrderItemRepository orderItemRepository;
    private final ReviewRepository reviewRepository;

    /** The artisan's lines in placed orders (open carts excluded). */
    public List<OrderItem> soldItems(Long artisanId) {
        return orderItemRepository.findByProductArtisanUserId(artisanId).stream()
                .filter(o -> o.getOrder() != null && !OrderStatus.CART.equalsIgnoreCase(o.getOrder().getStatus()))
                .toList();
    }

    public static BigDecimal lineTotal(OrderItem item) {
        return item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
    }

    private static boolean refunded(OrderItem item) {
        return RefundStatus.APPROVED.equalsIgnoreCase(item.getAdminRefundStatus());
    }

    public BigDecimal revenue(List<OrderItem> items) {
        return items.stream().filter(i -> !refunded(i)).map(ArtisanStatsService::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal revenueBetween(List<OrderItem> items, LocalDate from, LocalDate to) {
        return revenue(items.stream()
                .filter(i -> i.getOrder().getDate() != null)
                .filter(i -> !i.getOrder().getDate().isBefore(from) && !i.getOrder().getDate().isAfter(to))
                .toList());
    }

    /**
     * One row per order, with the artisan's own subtotal (not the whole order's total) and the fulfilment
     * status of the artisan's own lines.
     */
    public List<ArtisanOrderSummary> orderSummaries(List<OrderItem> items) {
        Map<Long, List<OrderItem>> byOrder = items.stream().collect(Collectors.groupingBy(
                i -> i.getOrder().getOrderId(), LinkedHashMap::new, Collectors.toList()));
        return byOrder.values().stream()
                .map(lines -> new ArtisanOrderSummary(
                        lines.get(0).getOrder(),
                        lines.stream().map(i -> i.getProduct() != null ? i.getProduct().getTitle() : "Product")
                                .distinct().collect(Collectors.joining(", ")),
                        lines.stream().map(ArtisanStatsService::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add),
                        OrderStatus.aggregate(lines.stream().map(OrderItem::getFulfilmentStatus).toList())))
                .sorted(Comparator.comparing((ArtisanOrderSummary s) -> s.order().getDate(),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /** Weekly revenue for the last 8 weeks (Monday-based), oldest first. */
    public List<BigDecimal> weeklyRevenue(List<OrderItem> items, LocalDate today) {
        List<BigDecimal> result = new ArrayList<>();
        LocalDate thisMonday = today.with(DayOfWeek.MONDAY);
        for (int i = 7; i >= 0; i--) {
            LocalDate start = thisMonday.minusWeeks(i);
            result.add(revenueBetween(items, start, start.plusDays(6)));
        }
        return result;
    }

    /** Monthly revenue for the last 6 months, oldest first. */
    public List<BigDecimal> monthlyRevenue(List<OrderItem> items, LocalDate today) {
        List<BigDecimal> result = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            LocalDate month = today.minusMonths(i);
            result.add(revenueBetween(items, month.withDayOfMonth(1), month.withDayOfMonth(month.lengthOfMonth())));
        }
        return result;
    }

    public static double percentChange(BigDecimal current, BigDecimal previous) {
        if (previous.signum() == 0) {
            return current.signum() > 0 ? 100.0 : 0.0;
        }
        return current.subtract(previous).multiply(BigDecimal.valueOf(100))
                .divide(previous, 1, RoundingMode.HALF_UP).doubleValue();
    }

    public static double percentChange(long current, long previous) {
        if (previous == 0) {
            return current > 0 ? 100.0 : 0.0;
        }
        return Math.round((current - previous) * 1000.0 / previous) / 10.0;
    }

    public List<Map<String, Object>> bestSellers(List<OrderItem> items, int limit) {
        Map<Product, int[]> qty = new HashMap<>();
        Map<Product, BigDecimal> revenue = new HashMap<>();
        for (OrderItem item : items) {
            if (item.getProduct() == null || refunded(item)) {
                continue;
            }
            qty.computeIfAbsent(item.getProduct(), p -> new int[1])[0] += item.getQuantity();
            revenue.merge(item.getProduct(), lineTotal(item), BigDecimal::add);
        }
        return qty.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]))
                .limit(limit)
                .map(e -> {
                    Product p = e.getKey();
                    Map<String, Object> row = new HashMap<>();
                    row.put("title", p.getTitle());
                    row.put("category", p.getCategory());
                    row.put("unitsSold", e.getValue()[0]);
                    row.put("revenue", revenue.get(p));
                    row.put("imageUrl", p.getImageUrl());
                    return row;
                })
                .toList();
    }

    /** Average rating, count, and per-star distribution of the artisan's non-rejected reviews. */
    public ReviewSummary reviewSummary(Long artisanId) {
        List<Review> reviews = reviewRepository.findByProductArtisanUserId(artisanId).stream()
                .filter(r -> r.getStatus() == null || !"rejected".equalsIgnoreCase(r.getStatus()))
                .toList();
        int[] distribution = new int[5];
        for (Review r : reviews) {
            if (r.getRating() >= 1 && r.getRating() <= 5) {
                distribution[r.getRating() - 1]++;
            }
        }
        double avg = reviews.stream().mapToInt(Review::getRating).average().orElse(0.0);
        List<Integer> percents = new ArrayList<>();
        for (int count : distribution) {
            percents.add(reviews.isEmpty() ? 0 : (int) Math.round(100.0 * count / reviews.size()));
        }
        return new ReviewSummary(avg, reviews.size(), distribution, percents);
    }

    public record ReviewSummary(double average, int count, int[] distribution, List<Integer> percents) {
    }
}
