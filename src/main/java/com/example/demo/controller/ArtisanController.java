package com.example.demo.controller;

import com.example.demo.model.Artisan;
import com.example.demo.model.ArtisanOrderSummary;
import com.example.demo.model.OrderItem;
import com.example.demo.repository.*;
import com.example.demo.service.AuctionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class ArtisanController {

    @Autowired
    private ArtisanRepository artisanRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private AuctionService auctionService;

    @Autowired
    private ReviewRepository reviewRepository;

    private void initEmptyModel(Model model) {
        model.addAttribute("productsCount", 0L);
        model.addAttribute("activeAuctions", 0L);
        model.addAttribute("pendingOrders", 0L);
        model.addAttribute("totalRevenue", 0.0);
        model.addAttribute("recentOrders", Collections.emptyList());
        model.addAttribute("products", Collections.emptyList());
        model.addAttribute("orders", Collections.emptyList());
        model.addAttribute("auctions", Collections.emptyList());
        model.addAttribute("ordersCount", 0L);
        model.addAttribute("revenuePercentage", 0.0);
        model.addAttribute("ordersPercentage", 0.0);
        model.addAttribute("weeklyRevenue", java.util.Arrays.asList(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0));
        model.addAttribute("monthlyRevenue", java.util.Arrays.asList(0.0, 0.0, 0.0, 0.0, 0.0, 0.0));
        model.addAttribute("bestSellers", Collections.emptyList());
        model.addAttribute("averageRating", 0.0);
        model.addAttribute("totalReviewsCount", 0);
        model.addAttribute("starPercent", java.util.Arrays.asList(0, 0, 0, 0, 0));
        model.addAttribute("starDistribution", new int[5]);
    }

    private String checkArtisanStatus(Long id) {
        if (id == null) {
            return null;
        }
        return artisanRepository.findById(id).map(artisan -> {
            String status = artisan.getStatus();
            if (!"active".equalsIgnoreCase(status)) {
                try {
                    return "redirect:/login?message=" + java.net.URLEncoder.encode(
                            "Your artisan account status is currently: " + status
                                    + ". Please wait for approval or activation.",
                            "UTF-8");
                } catch (Exception e) {
                    return "redirect:/login?message=AccessDenied";
                }
            }
            return null;
        }).orElse(null);
    }

    private void loadArtisan(Long artisanId, Model model) {
        if (artisanId == null) {
            model.addAttribute("artisan", null);
            return;
        }
        artisanRepository.findById(artisanId).ifPresentOrElse(
                artisan -> model.addAttribute("artisan", artisan),
                () -> model.addAttribute("artisan", null));
    }

    private List<OrderItem> getArtisanOrderItems(Long artisanId) {
        if (artisanId == null) {
            return Collections.emptyList();
        }
        return orderItemRepository.findByProductArtisanUserId(artisanId).stream()
                .filter(o -> o.getOrder() != null && !"cart".equalsIgnoreCase(o.getOrder().getStatus()))
                .toList();
    }

    /**
     * Total revenue = sum of (price × qty) for regular order items
     * + sum of currentHighestBid for ENDED auctions belonging to this artisan
     */
    private double computeTotalRevenue(Long artisanId, List<OrderItem> orders) {
        // Revenue from standard product orders (exclude auction-item order lines to
        // avoid double-counting)
        double orderRevenue = orders.stream()
                .filter(o -> o.getProduct() != null && !o.getProduct().isAuctionItem())
                .mapToDouble(o -> o.getPrice() * o.getQuantity())
                .sum();

        // Revenue from ended auctions: use the highest bid (actual sale price)
        double auctionRevenue = auctionRepository
                .findByProductArtisanUserIdAndStatus(artisanId, "ENDED")
                .stream()
                .filter(a -> a.getHighestBidder() != null) // only sold auctions
                .mapToDouble(a -> a.getCurrentHighestBid())
                .sum();

        return orderRevenue + auctionRevenue;
    }

    private List<ArtisanOrderSummary> buildOrderSummaries(List<OrderItem> orderItems) {
        var orderGroups = orderItems.stream()
                .collect(Collectors.groupingBy(
                        item -> item.getOrder().getOrderId(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        return orderGroups.values().stream()
                .map(items -> {
                    var order = items.get(0).getOrder();
                    var productNames = items.stream()
                            .map(item -> item.getProduct() != null ? item.getProduct().getTitle() : "Product")
                            .distinct()
                            .collect(Collectors.joining(", "));
                    var totalAmount = order.getTotalAmount() > 0
                            ? order.getTotalAmount()
                            : items.stream().mapToDouble(item -> item.getPrice() * item.getQuantity()).sum();
                    return new ArtisanOrderSummary(order, productNames, totalAmount);
                })
                .toList();
    }

    @GetMapping("/artisanDashboard")
    public String dashboard(@RequestParam(required = false) Long id, Model model) {
        String redirect = checkArtisanStatus(id);
        if (redirect != null)
            return redirect;
        initEmptyModel(model);
        loadArtisan(id, model);
        if (id != null) {
            model.addAttribute("productsCount", productRepository.countByArtisanUserId(id));
            model.addAttribute("activeAuctions", auctionRepository.countByProductArtisanUserIdAndStatus(id, "LIVE"));
            List<OrderItem> orders = getArtisanOrderItems(id);
            model.addAttribute("pendingOrders", orders.stream()
                    .filter(o -> "pending".equalsIgnoreCase(o.getOrder().getStatus())).count());
            model.addAttribute("totalRevenue", computeTotalRevenue(id, orders));
            model.addAttribute("recentOrders", buildOrderSummaries(orders));
        }
        return "artisanDashboard";
    }

    @GetMapping("/artisanProducts")
    public String products(@RequestParam(required = false) Long id, Model model) {
        String redirect = checkArtisanStatus(id);
        if (redirect != null)
            return redirect;
        initEmptyModel(model);
        loadArtisan(id, model);
        if (id != null) {
            model.addAttribute("products", productRepository.findByArtisanUserIdAndIsAuctionItem(id, false));
            model.addAttribute("productsCount", productRepository.countByArtisanUserIdAndIsAuctionItem(id, false));
        }
        return "artisanProducts";
    }

    @GetMapping("/artisanAuction")
    public String auctions(@RequestParam(required = false) Long id, Model model) {
        String redirect = checkArtisanStatus(id);
        if (redirect != null)
            return redirect;
        initEmptyModel(model);
        loadArtisan(id, model);
        // Sync auction statuses so ENDED auctions are correctly marked
        try {
            auctionService.syncStoredStatuses();
        } catch (Exception ignored) {
        }
        if (id != null) {
            model.addAttribute("auctions", auctionRepository.findByProductArtisanUserId(id));
            // Pass the artisan's non-auction products so the "Add Auction" modal
            // dropdown can be populated dynamically from the database.
            model.addAttribute("products", productRepository.findByArtisanUserIdAndIsAuctionItem(id, false));
        }
        return "artisanAuction";
    }

    @GetMapping("/artisanOrders")
    public String orders(@RequestParam(required = false) Long id, Model model) {
        String redirect = checkArtisanStatus(id);
        if (redirect != null)
            return redirect;
        initEmptyModel(model);
        loadArtisan(id, model);
        if (id != null) {
            List<OrderItem> orderItems = getArtisanOrderItems(id);
            var orderSummaries = buildOrderSummaries(orderItems);
            model.addAttribute("orders", orderSummaries);
            model.addAttribute("ordersCount", (long) orderSummaries.size());
        }
        return "artisanOrders";
    }

    @GetMapping("/artisanAnalytics")
    public String analytics(@RequestParam(required = false) Long id, Model model) {
        String redirect = checkArtisanStatus(id);
        if (redirect != null)
            return redirect;
        initEmptyModel(model);
        loadArtisan(id, model);
        if (id != null) {
            model.addAttribute("productsCount", productRepository.countByArtisanUserId(id));
            model.addAttribute("activeAuctions", auctionRepository.countByProductArtisanUserIdAndStatus(id, "LIVE"));

            List<OrderItem> orders = getArtisanOrderItems(id);
            model.addAttribute("pendingOrders", orders.stream()
                    .filter(o -> "pending".equalsIgnoreCase(o.getOrder().getStatus())).count());

            double totalRevenue = computeTotalRevenue(id, orders);
            model.addAttribute("totalRevenue", totalRevenue);
            model.addAttribute("ordersCount", (long) orders.size());

            // 1. Weekly and Monthly Revenue Calculation
            java.time.LocalDate today = java.time.LocalDate.now();
            List<Double> weeklyRevList = new java.util.ArrayList<>();
            for (int i = 7; i >= 0; i--) {
                java.time.LocalDate start = today.minusWeeks(i)
                        .minusDays(today.minusWeeks(i).getDayOfWeek().getValue() - 1);
                java.time.LocalDate end = start.plusDays(6);
                double weekRev = orders.stream()
                        .filter(o -> o.getOrder() != null && o.getOrder().getDate() != null)
                        .filter(o -> !o.getOrder().getDate().isBefore(start) && !o.getOrder().getDate().isAfter(end))
                        .filter(o -> o.getProduct() != null && !o.getProduct().isAuctionItem())
                        .mapToDouble(o -> o.getPrice() * o.getQuantity())
                        .sum();

                double weekAuctionRev = auctionRepository.findByProductArtisanUserIdAndStatus(id, "ENDED").stream()
                        .filter(a -> a.getHighestBidder() != null && a.getEndTime() != null)
                        .filter(a -> !a.getEndTime().toLocalDate().isBefore(start)
                                && !a.getEndTime().toLocalDate().isAfter(end))
                        .mapToDouble(a -> a.getCurrentHighestBid())
                        .sum();

                weeklyRevList.add(weekRev + weekAuctionRev);
            }
            model.addAttribute("weeklyRevenue", weeklyRevList);

            List<Double> monthlyRevList = new java.util.ArrayList<>();
            for (int i = 5; i >= 0; i--) {
                java.time.LocalDate targetMonth = today.minusMonths(i);
                java.time.LocalDate start = targetMonth.withDayOfMonth(1);
                java.time.LocalDate end = targetMonth.withDayOfMonth(targetMonth.lengthOfMonth());
                double monthRev = orders.stream()
                        .filter(o -> o.getOrder() != null && o.getOrder().getDate() != null)
                        .filter(o -> !o.getOrder().getDate().isBefore(start) && !o.getOrder().getDate().isAfter(end))
                        .filter(o -> o.getProduct() != null && !o.getProduct().isAuctionItem())
                        .mapToDouble(o -> o.getPrice() * o.getQuantity())
                        .sum();

                double monthAuctionRev = auctionRepository.findByProductArtisanUserIdAndStatus(id, "ENDED").stream()
                        .filter(a -> a.getHighestBidder() != null && a.getEndTime() != null)
                        .filter(a -> !a.getEndTime().toLocalDate().isBefore(start)
                                && !a.getEndTime().toLocalDate().isAfter(end))
                        .mapToDouble(a -> a.getCurrentHighestBid())
                        .sum();

                monthlyRevList.add(monthRev + monthAuctionRev);
            }
            model.addAttribute("monthlyRevenue", monthlyRevList);

            // 2. Percentage Changes
            double revenueThisMonth = monthlyRevList.get(5);
            double revenueLastMonth = monthlyRevList.get(4);
            double revenuePercent = 0.0;
            if (revenueLastMonth > 0) {
                revenuePercent = ((revenueThisMonth - revenueLastMonth) / revenueLastMonth) * 100;
            } else if (revenueThisMonth > 0) {
                revenuePercent = 100.0;
            }
            model.addAttribute("revenuePercentage", revenuePercent);

            long ordersThisMonth = orders.stream()
                    .filter(o -> o.getOrder() != null && o.getOrder().getDate() != null)
                    .filter(o -> !o.getOrder().getDate().isBefore(today.withDayOfMonth(1)))
                    .count();

            java.time.LocalDate startOfLastMonth = today.minusMonths(1).withDayOfMonth(1);
            java.time.LocalDate endOfLastMonth = today.minusMonths(1)
                    .withDayOfMonth(today.minusMonths(1).lengthOfMonth());
            long ordersLastMonth = orders.stream()
                    .filter(o -> o.getOrder() != null && o.getOrder().getDate() != null)
                    .filter(o -> !o.getOrder().getDate().isBefore(startOfLastMonth)
                            && !o.getOrder().getDate().isAfter(endOfLastMonth))
                    .count();

            double ordersPercent = 0.0;
            if (ordersLastMonth > 0) {
                ordersPercent = ((double) (ordersThisMonth - ordersLastMonth) / ordersLastMonth) * 100;
            } else if (ordersThisMonth > 0) {
                ordersPercent = 100.0;
            }
            model.addAttribute("ordersPercentage", ordersPercent);

            // 3. Best Selling Products
            java.util.Map<com.example.demo.model.Product, Integer> productQtyMap = new java.util.HashMap<>();
            for (OrderItem item : orders) {
                if (item.getProduct() != null) {
                    productQtyMap.put(item.getProduct(),
                            productQtyMap.getOrDefault(item.getProduct(), 0) + item.getQuantity());
                }
            }

            List<java.util.Map<String, Object>> bestSellers = productQtyMap.entrySet().stream()
                    .map(entry -> {
                        com.example.demo.model.Product p = entry.getKey();
                        int qty = entry.getValue();
                        double rev = p.getPrice() * qty;
                        java.util.Map<String, Object> map = new java.util.HashMap<>();
                        map.put("title", p.getTitle());
                        map.put("category", p.getCategory());
                        map.put("unitsSold", qty);
                        map.put("revenue", rev);
                        map.put("imageUrl", p.getImageUrl());
                        return map;
                    })
                    .sorted((m1, m2) -> Integer.compare((int) m2.get("unitsSold"), (int) m1.get("unitsSold")))
                    .limit(5)
                    .toList();
            model.addAttribute("bestSellers", bestSellers);

            // 4. Reviews Summary
            List<com.example.demo.model.Review> reviews = reviewRepository.findByProductArtisanUserId(id).stream()
                    .filter(r -> r.getStatus() == null || !"rejected".equalsIgnoreCase(r.getStatus()))
                    .toList();
            double avgRating = 0.0;
            int totalReviews = reviews.size();
            int[] starDistribution = new int[5];

            if (totalReviews > 0) {
                double sum = 0;
                for (com.example.demo.model.Review r : reviews) {
                    sum += r.getRating();
                    int rating = r.getRating();
                    if (rating >= 1 && rating <= 5) {
                        starDistribution[rating - 1]++;
                    }
                }
                avgRating = sum / totalReviews;
            }

            List<Integer> starPercent = new java.util.ArrayList<>();
            for (int i = 0; i < 5; i++) {
                if (totalReviews > 0) {
                    starPercent.add((int) Math.round(((double) starDistribution[i] / totalReviews) * 100));
                } else {
                    starPercent.add(0);
                }
            }

            model.addAttribute("averageRating", avgRating);
            model.addAttribute("totalReviewsCount", totalReviews);
            model.addAttribute("starPercent", starPercent);
            model.addAttribute("starDistribution", starDistribution);
        }
        return "artisanAnalytics";
    }

    @GetMapping("/artisanSettings")
    public String settings(@RequestParam(required = false) Long id, Model model) {
        String redirect = checkArtisanStatus(id);
        if (redirect != null)
            return redirect;
        initEmptyModel(model);
        loadArtisan(id, model);
        return "artisanSettings";
    }

    @GetMapping("/artisanOrderDetail")
    public String ordersDetail(@RequestParam(required = false) Long id,
            @RequestParam(required = false) Long orderId,
            Model model) {
        String redirect = checkArtisanStatus(id);
        if (redirect != null)
            return redirect;
        initEmptyModel(model);
        loadArtisan(id, model);

        if (id != null && orderId != null) {
            orderRepository.findById(orderId).ifPresent(order -> {
                model.addAttribute("order", order);

                List<OrderItem> allItems = orderItemRepository.findByOrderIdWithProduct(orderId);
                List<OrderItem> artisanItems = allItems.stream()
                        .filter(item -> item.getProduct() != null
                                && item.getProduct().getArtisan() != null
                                && item.getProduct().getArtisan().getUserId().equals(id))
                        .toList();
                model.addAttribute("orderItems", artisanItems);

                double artisanSubtotal = artisanItems.stream()
                        .mapToDouble(item -> item.getPrice() * item.getQuantity())
                        .sum();
                model.addAttribute("artisanSubtotal", artisanSubtotal);
            });
        }
        return "artisanOrderDetail";
    }

    @GetMapping("/artisanNotification")
    public String notifications(@RequestParam(required = false) Long id, Model model) {
        String redirect = checkArtisanStatus(id);
        if (redirect != null)
            return redirect;
        initEmptyModel(model);
        loadArtisan(id, model);
        return "artisanNotification";
    }
}
