package com.example.demo.service;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminReportsService {

    private static final String[] DONUT_COLORS = {
            "#c17c5f", "#8b7355", "#a5664d", "#d4c5b5", "#f5ebe0", "#e5e0d8"
    };

    private final OrderRepository orderRepository;

    private final OrderItemRepository orderItemRepository;

    private final UserRepository userRepository;

    private final ArtisanRepository artisanRepository;

    private final AuctionRepository auctionRepository;

    private final ReviewRepository reviewRepository;

    private final AuctionService auctionService;

    public record DatePeriod(LocalDate start, LocalDate end, String label) {
        public long daysInclusive() {
            return ChronoUnit.DAYS.between(start, end) + 1;
        }

        public DatePeriod previousPeriod() {
            long days = daysInclusive();
            LocalDate prevEnd = start.minusDays(1);
            LocalDate prevStart = prevEnd.minusDays(days - 1);
            return new DatePeriod(prevStart, prevEnd, label + " (previous)");
        }

        public boolean contains(LocalDate date) {
            return date != null && !date.isBefore(start) && !date.isAfter(end);
        }

        public boolean contains(LocalDateTime dateTime) {
            return dateTime != null && contains(dateTime.toLocalDate());
        }
    }

    public DatePeriod resolvePeriod(String range, LocalDate customStart, LocalDate customEnd) {
        LocalDate today = LocalDate.now();
        return switch (range == null ? "month" : range.toLowerCase()) {
            case "week" -> new DatePeriod(
                    today.with(java.time.DayOfWeek.MONDAY),
                    today,
                    "This Week");
            case "3months" -> new DatePeriod(today.minusMonths(3).plusDays(1), today, "Last 3 Months");
            case "year" -> new DatePeriod(today.withDayOfYear(1), today, "This Year");
            case "custom" -> {
                LocalDate start = customStart != null ? customStart : today.minusMonths(1);
                LocalDate end = customEnd != null ? customEnd : today;
                if (start.isAfter(end)) {
                    LocalDate tmp = start;
                    start = end;
                    end = tmp;
                }
                yield new DatePeriod(start, end, "Custom Range");
            }
            default -> new DatePeriod(today.withDayOfMonth(1), today, "This Month");
        };
    }

    @Transactional(readOnly = true)
    public Map<String, Object> buildReport(String range, LocalDate customStart, LocalDate customEnd) {
        DatePeriod period = resolvePeriod(range, customStart, customEnd);
        DatePeriod previous = period.previousPeriod();

        // Only load what the report needs: the selected period plus the previous one (for the % change).
        // Open carts (status "cart") are not sales and are excluded by the queries.
        LocalDate from = previous.start();
        LocalDate to = period.end();
        List<Order> allOrders = orderRepository.findPlacedBetween(from, to);
        List<OrderItem> allItems = orderItemRepository.findPlacedItemsBetween(from, to);
        List<User> allUsers = userRepository.findByJoinDateBetween(from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        List<Auction> allAuctions = auctionRepository.findByEndTimeBetween(from.atStartOfDay(), to.plusDays(1).atStartOfDay());

        List<Order> periodOrders = filterOrdersByDate(allOrders, period);
        List<Order> prevOrders = filterOrdersByDate(allOrders, previous);
        List<OrderItem> periodItems = filterItemsByOrderDate(allItems, period);

        double totalRevenue = sumOrderRevenue(periodOrders);
        double prevRevenue = sumOrderRevenue(prevOrders);
        long totalOrders = periodOrders.size();
        long prevOrderCount = prevOrders.size();

        long newUsers = countNewUsers(allUsers, period, false);
        long prevNewUsers = countNewUsers(allUsers, previous, false);

        long activeArtisans = artisanRepository.findByStatus("active").size();
        long newArtisansInPeriod = countNewUsers(allUsers, period, true);
        long prevNewArtisans = countNewUsers(allUsers, previous, true);

        double auctionRevenue = sumAuctionRevenue(allAuctions, period);
        double prevAuctionRevenue = sumAuctionRevenue(allAuctions, previous);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("label", period.label());
        result.put("startDate", period.start().toString());
        result.put("endDate", period.end().toString());

        result.put("totalRevenue", totalRevenue);
        result.put("totalRevenueChange", percentChange(totalRevenue, prevRevenue));
        result.put("totalOrders", totalOrders);
        result.put("totalOrdersChange", percentChange(totalOrders, prevOrderCount));
        result.put("newUsers", newUsers);
        result.put("newUsersChange", percentChange(newUsers, prevNewUsers));
        result.put("activeArtisans", activeArtisans);
        result.put("newArtisansInPeriod", newArtisansInPeriod);
        result.put("activeArtisansChange", percentChange(newArtisansInPeriod, prevNewArtisans));
        result.put("auctionRevenue", auctionRevenue);
        result.put("auctionRevenueChange", percentChange(auctionRevenue, prevAuctionRevenue));

        result.put("revenuePerWeek", toDoubleList(bucketRevenue(periodOrders, period)));
        result.put("weekLabels", Arrays.asList(bucketLabels(period, 6)));

        Map<String, Object> categoryData = buildCategoryBreakdown(periodItems, totalOrders);
        result.put("categoryNames", categoryData.get("names"));
        result.put("categoryCounts", categoryData.get("counts"));
        result.put("categoryPercentages", categoryData.get("percentages"));

        result.put("regCustomers", toIntList(bucketRegistrations(allUsers, period, false)));
        result.put("regArtisans", toIntList(bucketRegistrations(allUsers, period, true)));

        Map<String, Object> artisanData = buildTopArtisans(periodItems);
        result.put("topArtisanNames", artisanData.get("names"));
        result.put("topArtisanRevenues", artisanData.get("revenues"));
        result.put("topArtisanPercentages", artisanData.get("percentages"));

        result.put("bestProducts", buildBestProducts(periodItems));
        result.put("donutColors", DONUT_COLORS);

        return result;
    }

    private boolean isArtisanRole(String role) {
        return role != null && "ARTISAN".equalsIgnoreCase(role.trim());
    }

    private List<Order> filterOrdersByDate(List<Order> orders, DatePeriod period) {
        return orders.stream()
                .filter(o -> period.contains(o.getDate()))
                .collect(Collectors.toList());
    }

    private List<OrderItem> filterItemsByOrderDate(List<OrderItem> items, DatePeriod period) {
        return items.stream()
                .filter(i -> i.getOrder() != null && period.contains(i.getOrder().getDate()))
                .collect(Collectors.toList());
    }

    private double sumOrderRevenue(List<Order> orders) {
        // Reports aggregate in double for charting; money is stored and computed as BigDecimal elsewhere.
        return orders.stream().mapToDouble(o -> o.getTotalAmount().doubleValue()).sum();
    }

    private long countNewUsers(List<User> users, DatePeriod period, boolean artisansOnly) {
        return users.stream()
                .filter(u -> u.getJoinDate() != null && period.contains(u.getJoinDate()))
                .filter(u -> {
                    if ("ADMIN".equalsIgnoreCase(u.getRole())) {
                        return false;
                    }
                    return artisansOnly == isArtisanRole(u.getRole());
                })
                .count();
    }

    private boolean isAuctionEnded(Auction auction) {
        return AuctionService.DISPLAY_ENDED.equals(auctionService.resolveDisplayStatus(auction));
    }

    private double sumAuctionRevenue(List<Auction> auctions, DatePeriod period) {
        return auctions.stream()
                .filter(this::isAuctionEnded)
                .filter(a -> a.getEndTime() != null && period.contains(a.getEndTime()))
                .filter(a -> a.getCurrentHighestBid().signum() > 0)
                .mapToDouble(a -> a.getCurrentHighestBid().doubleValue())
                .sum();
    }

    private List<Double> toDoubleList(double[] arr) {
        List<Double> list = new ArrayList<>();
        for (double v : arr) {
            list.add(v);
        }
        return list;
    }

    private List<Integer> toIntList(int[] arr) {
        List<Integer> list = new ArrayList<>();
        for (int v : arr) {
            list.add(v);
        }
        return list;
    }

    private double percentChange(double current, double previous) {
        if (previous == 0) {
            return current > 0 ? 100.0 : 0.0;
        }
        return Math.round((current - previous) / previous * 1000.0) / 10.0;
    }

    private double[] bucketRevenue(List<Order> orders, DatePeriod period) {
        double[] buckets = new double[6];
        long totalDays = period.daysInclusive();
        if (totalDays <= 0) {
            return buckets;
        }
        for (Order order : orders) {
            if (order.getDate() == null) {
                continue;
            }
            long dayOffset = ChronoUnit.DAYS.between(period.start(), order.getDate());
            int bucket = (int) Math.min(5, (dayOffset * 6) / totalDays);
            buckets[bucket] += order.getTotalAmount().doubleValue();
        }
        return buckets;
    }

    private int[] bucketRegistrations(List<User> users, DatePeriod period, boolean artisansOnly) {
        int[] buckets = new int[6];
        long totalDays = period.daysInclusive();
        if (totalDays <= 0) {
            return buckets;
        }
        for (User user : users) {
            if (user.getJoinDate() == null || !period.contains(user.getJoinDate())) {
                continue;
            }
            if ("ADMIN".equalsIgnoreCase(user.getRole())) {
                continue;
            }
            boolean isArtisan = isArtisanRole(user.getRole());
            if (artisansOnly != isArtisan) {
                continue;
            }
            long dayOffset = ChronoUnit.DAYS.between(period.start(), user.getJoinDate().toLocalDate());
            int bucket = (int) Math.min(5, (dayOffset * 6) / totalDays);
            buckets[bucket]++;
        }
        return buckets;
    }

    private String[] bucketLabels(DatePeriod period, int count) {
        String[] labels = new String[count];
        long totalDays = period.daysInclusive();
        if (totalDays <= 7) {
            for (int i = 0; i < count; i++) {
                labels[i] = "Day " + (i + 1);
            }
        } else if (totalDays <= 35) {
            for (int i = 0; i < count; i++) {
                labels[i] = "Week " + (i + 1);
            }
        } else {
            for (int i = 0; i < count; i++) {
                labels[i] = "P" + (i + 1);
            }
        }
        return labels;
    }

    private Map<String, Object> buildCategoryBreakdown(List<OrderItem> items, long orderCountForCenter) {
        Map<String, Long> categoryCount = new LinkedHashMap<>();
        for (OrderItem item : items) {
            String cat = (item.getProduct() != null && item.getProduct().getCategory() != null)
                    ? item.getProduct().getCategory()
                    : "Other";
            categoryCount.merge(cat, (long) item.getQuantity(), Long::sum);
        }

        List<Map.Entry<String, Long>> sorted = new ArrayList<>(categoryCount.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

        List<String> names = new ArrayList<>();
        List<Long> counts = new ArrayList<>();
        List<Double> percentages = new ArrayList<>();
        long total = sorted.stream().mapToLong(Map.Entry::getValue).sum();

        for (var entry : sorted) {
            names.add(entry.getKey());
            counts.add(entry.getValue());
            percentages.add(total > 0 ? Math.round(entry.getValue() * 1000.0 / total) / 10.0 : 0.0);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("names", names);
        data.put("counts", counts);
        data.put("percentages", percentages);
        data.put("totalOrders", orderCountForCenter);
        return data;
    }

    private Map<String, Object> buildTopArtisans(List<OrderItem> items) {
        Map<String, Double> artisanRevenue = new LinkedHashMap<>();
        for (OrderItem item : items) {
            String artisanName = (item.getProduct() != null && item.getProduct().getArtisanName() != null)
                    ? item.getProduct().getArtisanName()
                    : "Unknown";
            artisanRevenue.merge(artisanName, item.getPrice().doubleValue() * item.getQuantity(), Double::sum);
        }

        List<Map.Entry<String, Double>> sorted = new ArrayList<>(artisanRevenue.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        List<String> names = new ArrayList<>();
        List<Double> revenues = new ArrayList<>();
        List<Double> percentages = new ArrayList<>();
        double maxRevenue = sorted.isEmpty() ? 1.0 : sorted.get(0).getValue();

        for (int i = 0; i < Math.min(5, sorted.size()); i++) {
            var entry = sorted.get(i);
            names.add(entry.getKey());
            revenues.add(entry.getValue());
            percentages.add(Math.round(entry.getValue() * 100.0 / maxRevenue * 10.0) / 10.0);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("names", names);
        data.put("revenues", revenues);
        data.put("percentages", percentages);
        return data;
    }

    private List<Map<String, Object>> buildBestProducts(List<OrderItem> items) {
        Map<Long, int[]> productSales = new LinkedHashMap<>();
        Map<Long, Double> productRevenue = new LinkedHashMap<>();
        Map<Long, Product> productMap = new LinkedHashMap<>();

        for (OrderItem item : items) {
            if (item.getProduct() == null) {
                continue;
            }
            Long pid = item.getProduct().getId();
            productSales.computeIfAbsent(pid, k -> new int[] { 0 })[0] += item.getQuantity();
            productRevenue.merge(pid, item.getPrice().doubleValue() * item.getQuantity(), Double::sum);
            productMap.putIfAbsent(pid, item.getProduct());
        }

        // Average rating per product in one grouped query (rows of [productId, avgRating]).
        Map<Long, Double> productRatings = new LinkedHashMap<>();
        if (!productSales.isEmpty()) {
            for (Object[] row : reviewRepository.averageRatingByProduct(productSales.keySet())) {
                productRatings.put((Long) row[0], ((Number) row[1]).doubleValue());
            }
        }

        List<Map.Entry<Long, int[]>> sorted = new ArrayList<>(productSales.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]));

        List<Map<String, Object>> bestProducts = new ArrayList<>();
        for (int i = 0; i < Math.min(10, sorted.size()); i++) {
            var entry = sorted.get(i);
            Long pid = entry.getKey();
            Product p = productMap.get(pid);
            if (p == null) {
                continue;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rank", i + 1);
            row.put("title", p.getTitle());
            row.put("imageUrl", p.getImageUrl() != null && !p.getImageUrl().isBlank()
                    ? p.getImageUrl()
                    : "/images/placeholder-product.png");
            row.put("artisanName", p.getArtisanName() != null ? p.getArtisanName() : "Unknown");
            row.put("category", p.getCategory() != null ? p.getCategory() : "Other");
            row.put("unitsSold", entry.getValue()[0]);
            row.put("revenue", productRevenue.getOrDefault(pid, 0.0));
            double avgRating = Math.round(productRatings.getOrDefault(pid, 0.0) * 10.0) / 10.0;
            row.put("avgRating", avgRating);
            row.put("productId", pid);
            bestProducts.add(row);
        }
        return bestProducts;
    }

}
