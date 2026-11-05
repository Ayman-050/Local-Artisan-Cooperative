package com.example.demo.controller;

import com.example.demo.model.Artisan;
import com.example.demo.model.Order;
import com.example.demo.model.User;
import com.example.demo.model.Product;
import com.example.demo.repository.ArtisanRepository;
import com.example.demo.repository.AuctionRepository;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.ReviewRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.BidRepository;
import com.example.demo.service.AuctionService;
import com.example.demo.service.AdminReportsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import org.springframework.http.ResponseEntity;
import java.util.Map;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Controller
public class AdminController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ArtisanRepository artisanRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private BidRepository bidRepository;

    @Autowired
    private AuctionService auctionService;

    @Autowired
    private com.example.demo.repository.WishlistItemRepository wishlistItemRepository;

    @Autowired
    private com.example.demo.repository.OrderItemRepository orderItemRepository;

    @Autowired
    private AdminReportsService adminReportsService;

    @GetMapping("/adminDashboard")
    public String dashboard(Model model) {
        long totalUsers = userRepository.count();
        long totalArtisans = artisanRepository.count();
        List<com.example.demo.model.Product> allProducts = productRepository.findAll();
        LocalDate now = LocalDate.now();

        long activeProducts = allProducts.stream()
                .filter(p -> p.getAddingDate() != null &&
                        p.getAddingDate().getMonthValue() == now.getMonthValue() &&
                        p.getAddingDate().getYear() == now.getYear())
                .count();

        long productsLastMonth = allProducts.stream()
                .filter(p -> p.getAddingDate() != null &&
                        p.getAddingDate().getMonthValue() == now.minusMonths(1).getMonthValue() &&
                        p.getAddingDate().getYear() == now.minusMonths(1).getYear())
                .count();

        double productGrowth = 0;
        if (productsLastMonth > 0) {
            productGrowth = ((double) (activeProducts - productsLastMonth) / productsLastMonth) * 100;
        } else if (activeProducts > 0) {
            productGrowth = 100.0;
        }

        List<com.example.demo.model.Auction> allAuctions = auctionRepository.findAll();
        long openAuctions = allAuctions.stream()
                .filter(a -> a.getStartTime() != null &&
                        a.getStartTime().getMonthValue() == now.getMonthValue() &&
                        a.getStartTime().getYear() == now.getYear())
                .count();

        long auctionsLastMonth = allAuctions.stream()
                .filter(a -> a.getStartTime() != null &&
                        a.getStartTime().getMonthValue() == now.minusMonths(1).getMonthValue() &&
                        a.getStartTime().getYear() == now.minusMonths(1).getYear())
                .count();

        double auctionGrowth = 0;
        if (auctionsLastMonth > 0) {
            auctionGrowth = ((double) (openAuctions - auctionsLastMonth) / auctionsLastMonth) * 100;
        } else if (openAuctions > 0) {
            auctionGrowth = 100.0;
        }

        List<com.example.demo.model.Review> allReviews = reviewRepository.findAll();
        long totalReviewsThisMonth = allReviews.stream()
                .filter(r -> r.getDate() != null &&
                        r.getDate().getMonthValue() == now.getMonthValue() &&
                        r.getDate().getYear() == now.getYear())
                .count();

        long reviewsLastMonth = allReviews.stream()
                .filter(r -> r.getDate() != null &&
                        r.getDate().getMonthValue() == now.minusMonths(1).getMonthValue() &&
                        r.getDate().getYear() == now.minusMonths(1).getYear())
                .count();

        double reviewGrowth = 0;
        if (reviewsLastMonth > 0) {
            reviewGrowth = ((double) (totalReviewsThisMonth - reviewsLastMonth) / reviewsLastMonth) * 100;
        } else if (totalReviewsThisMonth > 0) {
            reviewGrowth = 100.0;
        }

        // Calculate dynamic user and artisan growth
        List<User> allUsers = userRepository.findAll();
        long usersThisMonth = allUsers.stream()
                .filter(u -> u.getJoinDate() != null &&
                        u.getJoinDate().getMonthValue() == now.getMonthValue() &&
                        u.getJoinDate().getYear() == now.getYear())
                .count();
        long usersLastMonth = allUsers.stream()
                .filter(u -> u.getJoinDate() != null &&
                        u.getJoinDate().getMonthValue() == now.minusMonths(1).getMonthValue() &&
                        u.getJoinDate().getYear() == now.minusMonths(1).getYear())
                .count();
        double userGrowth = percentChange(usersThisMonth, usersLastMonth);

        List<Artisan> allArtisans = artisanRepository.findAll();
        long artisansThisMonth = allArtisans.stream()
                .filter(a -> a.getJoinDate() != null &&
                        a.getJoinDate().getMonthValue() == now.getMonthValue() &&
                        a.getJoinDate().getYear() == now.getYear())
                .count();
        long artisansLastMonth = allArtisans.stream()
                .filter(a -> a.getJoinDate() != null &&
                        a.getJoinDate().getMonthValue() == now.minusMonths(1).getMonthValue() &&
                        a.getJoinDate().getYear() == now.minusMonths(1).getYear())
                .count();
        double artisanGrowth = percentChange(artisansThisMonth, artisansLastMonth);

        // Calculate pending refunds count & growth
        List<com.example.demo.model.OrderItem> refundItems = orderItemRepository.findAllWithOrderAndProduct().stream()
                .filter(com.example.demo.model.OrderItem::isRefundRequested)
                .toList();

        long pendingRefundsCount = refundItems.stream()
                .filter(item -> "PENDING".equalsIgnoreCase(item.getAdminRefundStatus()))
                .count();

        long refundRequestsThisMonth = refundItems.stream()
                .filter(item -> item.getOrder() != null && item.getOrder().getDate() != null &&
                        item.getOrder().getDate().getMonthValue() == now.getMonthValue() &&
                        item.getOrder().getDate().getYear() == now.getYear())
                .count();

        long refundRequestsLastMonth = refundItems.stream()
                .filter(item -> item.getOrder() != null && item.getOrder().getDate() != null &&
                        item.getOrder().getDate().getMonthValue() == now.minusMonths(1).getMonthValue() &&
                        item.getOrder().getDate().getYear() == now.minusMonths(1).getYear())
                .count();

        double pendingRefundsGrowth = percentChange(refundRequestsThisMonth, refundRequestsLastMonth);

        // Calculate counts for Quick Actions
        long pendingArtisansCount = artisanRepository.findByStatusIn(List.of("pending approval", "pending")).size();
        long flaggedReviewsCount = allReviews.stream()
                .filter(r -> Boolean.TRUE.equals(r.getAutoFlagged()))
                .count();

        // Build consolidated Recent Activity list
        List<RecentActivity> activities = new ArrayList<>();

        // 1. Artisan Registrations
        for (Artisan artisan : allArtisans) {
            if (artisan.getJoinDate() != null) {
                String status = artisan.getStatus();
                boolean isPending = "pending".equalsIgnoreCase(status) || "pending approval".equalsIgnoreCase(status);
                String description = "New artisan registration: <span class=\"font-semibold\">" + artisan.getName()
                        + "</span> " +
                        (isPending ? "applied to join" : "joined") + " the platform";
                activities.add(new RecentActivity(
                        "artisan_reg",
                        description,
                        artisan.getJoinDate(),
                        "fa-user-plus text-green-600",
                        "bg-green-50",
                        "/adminArtisanApproval"));
            }
        }

        // 2. Orders
        List<Order> allOrders = orderRepository.findAll();
        for (Order order : allOrders) {
            if (order.getDate() != null && !"cart".equalsIgnoreCase(order.getStatus())) {
                boolean isLarge = order.getTotalAmount() >= 100.0;
                String orderNum = "#ORD-" + order.getOrderId();
                String description = (isLarge ? "Large order placed: " : "New order placed: ")
                        + "Order <span class=\"font-mono font-semibold\">" + orderNum + "</span> worth "
                        + String.format("%.2f BD", order.getTotalAmount());
                activities.add(new RecentActivity(
                        "order",
                        description,
                        order.getDate().atStartOfDay(),
                        "fa-shopping-bag text-blue-600",
                        "bg-blue-50",
                        "/adminOrderManagment"));
            }
        }

        // 3. Refund Requests
        for (com.example.demo.model.OrderItem item : refundItems) {
            if (item.getOrder() != null && item.getOrder().getDate() != null) {
                String orderNum = "#ORD-" + item.getOrder().getOrderId();
                String description = "Refund request: Customer requested refund for order <span class=\"font-mono font-semibold\">"
                        + orderNum + "</span>";
                activities.add(new RecentActivity(
                        "refund",
                        description,
                        item.getOrder().getDate().atStartOfDay(),
                        "fa-exclamation-triangle text-red-600",
                        "bg-red-50",
                        "/adminRefund"));
            }
        }

        // 4. Completed Auctions
        for (com.example.demo.model.Auction auction : allAuctions) {
            if (auction.getEndTime() != null && "ended".equalsIgnoreCase(auction.getStatus())) {
                String prodTitle = auction.getProduct() != null ? auction.getProduct().getTitle() : "Product";
                String description = "Auction completed: <span class=\"font-semibold\">" + prodTitle
                        + "</span> sold for " + String.format("%.2f BD", auction.getCurrentHighestBid());
                activities.add(new RecentActivity(
                        "auction",
                        description,
                        auction.getEndTime(),
                        "fa-gavel text-purple-600",
                        "bg-purple-50",
                        "/adminAuction"));
            }
        }

        // 5. Flagged Reviews
        for (com.example.demo.model.Review review : allReviews) {
            if (review.getDate() != null && Boolean.TRUE.equals(review.getAutoFlagged())) {
                String prodTitle = review.getProduct() != null ? review.getProduct().getTitle() : "Product";
                String description = "Review flagged: Product review for <span class=\"font-semibold\">" + prodTitle
                        + "</span> reported as inappropriate";
                activities.add(new RecentActivity(
                        "review_flagged",
                        description,
                        review.getDate().atStartOfDay(),
                        "fa-flag text-orange-600",
                        "bg-orange-50",
                        "/adminReview"));
            }
        }

        // Sort all and take top 5
        List<RecentActivity> recentActivities = activities.stream()
                .sorted()
                .limit(5)
                .collect(Collectors.toList());

        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("totalArtisans", totalArtisans);
        model.addAttribute("activeProducts", activeProducts);
        model.addAttribute("openAuctions", openAuctions);
        model.addAttribute("totalReviews", totalReviewsThisMonth);
        model.addAttribute("pendingRefundsCount", pendingRefundsCount);

        model.addAttribute("userGrowth", userGrowth);
        model.addAttribute("artisanGrowth", artisanGrowth);
        model.addAttribute("productGrowth", productGrowth);
        model.addAttribute("auctionGrowth", auctionGrowth);
        model.addAttribute("reviewGrowth", reviewGrowth);
        model.addAttribute("pendingRefundsGrowth", pendingRefundsGrowth);

        model.addAttribute("pendingArtisansCount", pendingArtisansCount);
        model.addAttribute("flaggedReviewsCount", flaggedReviewsCount);
        model.addAttribute("recentActivities", recentActivities);

        // Orders per week (last 6 weeks)
        int[] ordersPerWeek = new int[6];
        for (Order order : allOrders) {
            if (order.getDate() != null) {
                long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(order.getDate(), now);
                if (daysBetween >= 0 && daysBetween < 42) {
                    int weekIndex = (int) (daysBetween / 7);
                    // weekIndex 0 is current week, up to 5 is 6 weeks ago
                    ordersPerWeek[5 - weekIndex]++;
                }
            }
        }
        model.addAttribute("ordersPerWeek", ordersPerWeek);

        // Revenue per month (last 6 months)
        double[] revenuePerMonth = new double[6];
        String[] monthLabels = new String[6];
        for (int i = 0; i < 6; i++) {
            LocalDate monthDate = now.minusMonths(5 - i);
            monthLabels[i] = monthDate.getMonth().name().substring(0, 3);
            final int currentMonth = monthDate.getMonthValue();
            final int currentYear = monthDate.getYear();

            double monthlyRevenue = allOrders.stream()
                    .filter(o -> o.getDate() != null && o.getDate().getMonthValue() == currentMonth
                            && o.getDate().getYear() == currentYear)
                    .mapToDouble(Order::getTotalAmount)
                    .sum();
            revenuePerMonth[i] = monthlyRevenue;
        }
        model.addAttribute("revenuePerMonth", revenuePerMonth);
        model.addAttribute("monthLabels", monthLabels);

        return "adminDashboard";
    }

    @GetMapping("/adminArtisanApproval")
    public String adminArtisanApproval(Model model) {
        List<Artisan> pendingArtisans = artisanRepository.findByStatusIn(List.of("pending approval", "pending"));
        List<Artisan> approvedArtisans = artisanRepository.findByStatus("active");
        List<Artisan> rejectedArtisans = artisanRepository.findByStatus("rejected");

        model.addAttribute("pendingArtisans", pendingArtisans);
        model.addAttribute("approvedArtisans", approvedArtisans);
        model.addAttribute("rejectedArtisans", rejectedArtisans);

        return "adminArtisanApproval";
    }

    @GetMapping("/adminAuction")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public String adminAuction(Model model) {
        auctionService.syncStoredStatuses();
        List<com.example.demo.model.Auction> allAuctions = auctionRepository.findAllWithProduct();

        Map<Long, Long> auctionBidsMap = new java.util.LinkedHashMap<>();
        Map<Long, String> auctionDisplayStatusMap = new java.util.LinkedHashMap<>();
        java.util.Set<String> categories = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        java.util.Set<String> artisans = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        for (com.example.demo.model.Auction a : allAuctions) {
            auctionBidsMap.put(a.getId(), bidRepository.countByAuctionId(a.getId()));
            String displayStatus = auctionService.resolveDisplayStatus(a);
            auctionDisplayStatusMap.put(a.getId(), displayStatus.toLowerCase());
            if (a.getProduct() != null) {
                if (a.getProduct().getCategory() != null && !a.getProduct().getCategory().trim().isEmpty()) {
                    categories.add(a.getProduct().getCategory().trim());
                }
                if (a.getProduct().getArtisanName() != null && !a.getProduct().getArtisanName().trim().isEmpty()) {
                    artisans.add(a.getProduct().getArtisanName().trim());
                }
            }
        }

        model.addAttribute("auctionsList", allAuctions);
        model.addAttribute("auctionBidsMap", auctionBidsMap);
        model.addAttribute("auctionDisplayStatusMap", auctionDisplayStatusMap);
        model.addAttribute("categoriesList", categories);
        model.addAttribute("artisansList", artisans);
        model.addAttribute("totalAuctions", allAuctions.size());

        return "adminAuction";
    }

    @GetMapping("/api/admin/auctions/{id}/monitor")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> adminAuctionMonitor(@PathVariable Long id) {
        return auctionService.buildAdminMonitorData(id)
                .map(data -> ResponseEntity.ok(Map.of("success", true, "data", data)))
                .orElse(ResponseEntity.status(404).body(Map.of("success", false, "message", "Auction not found")));
    }

    @PostMapping("/api/admin/auctions/{id}/extend")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> extendAuction(
            @PathVariable Long id,
            @RequestParam(defaultValue = "30") int minutes) {
        Map<String, Object> result = auctionService.extendAuction(id, minutes);
        boolean ok = Boolean.TRUE.equals(result.get("success"));
        return ok ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    @PostMapping("/api/admin/auctions/{id}/cancel")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> cancelAuction(@PathVariable Long id) {
        Map<String, Object> result = auctionService.cancelAuction(id);
        boolean ok = Boolean.TRUE.equals(result.get("success"));
        return ok ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    @PostMapping("/api/admin/auctions/{id}/flag")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> flagAuction(
            @PathVariable Long id,
            @RequestParam(required = false) String reason) {
        Map<String, Object> result = auctionService.flagAuction(id, reason);
        boolean ok = Boolean.TRUE.equals(result.get("success"));
        return ok ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    @GetMapping("/adminNotification")
    public String adminNotification() {
        return "adminNotification";
    }

    @GetMapping({ "/adminOrderDetail", "/adminOrderDetail/{id}" })
    public String adminOrderDetail(@PathVariable(required = false) Long id, Model model) {
        if (id != null) {
            orderRepository.findById(id).ifPresent(order -> {
                model.addAttribute("order", order);

                // Customer info
                com.example.demo.model.Customer customer = order.getCustomer();
                model.addAttribute("customer", customer);

                // Artisan info — derive from the first order item's product
                com.example.demo.model.Artisan artisan = null;
                if (order.getOrderItems() != null && !order.getOrderItems().isEmpty()) {
                    for (com.example.demo.model.OrderItem item : order.getOrderItems()) {
                        if (item.getProduct() != null && item.getProduct().getArtisan() != null) {
                            artisan = item.getProduct().getArtisan();
                            break;
                        }
                    }
                }
                model.addAttribute("artisan", artisan);

                // Payment info
                model.addAttribute("payment", order.getPayment());

                // Order items list
                model.addAttribute("orderItems", order.getOrderItems());
            });
        }
        return "adminOrderDetail";
    }

    @GetMapping("/adminOrderManagment")
    public String adminOrderManagment(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            Model model) {

        List<Order> orders = orderRepository.findAll();

        // Filter by search (order ID or customer name)
        if (search != null && !search.trim().isEmpty()) {
            String q = search.trim().toLowerCase();
            orders = orders.stream().filter(o -> {
                String idStr = String.valueOf(o.getOrderId());
                String customerName = o.getCustomer() != null && o.getCustomer().getName() != null
                        ? o.getCustomer().getName().toLowerCase()
                        : "";
                return idStr.contains(q) || customerName.contains(q);
            }).collect(java.util.stream.Collectors.toList());
        }

        // Filter by status
        if (status != null && !status.trim().isEmpty()) {
            String s = status.trim().toLowerCase();
            orders = orders.stream()
                    .filter(o -> o.getStatus() != null && o.getStatus().toLowerCase().equals(s))
                    .collect(java.util.stream.Collectors.toList());
        }

        // Filter by date range
        if (dateFrom != null && !dateFrom.trim().isEmpty()) {
            java.time.LocalDate from = java.time.LocalDate.parse(dateFrom);
            orders = orders.stream()
                    .filter(o -> o.getDate() != null && !o.getDate().isBefore(from))
                    .collect(java.util.stream.Collectors.toList());
        }
        if (dateTo != null && !dateTo.trim().isEmpty()) {
            java.time.LocalDate to = java.time.LocalDate.parse(dateTo);
            orders = orders.stream()
                    .filter(o -> o.getDate() != null && !o.getDate().isAfter(to))
                    .collect(java.util.stream.Collectors.toList());
        }

        // Sort by date descending
        orders.sort((o1, o2) -> {
            if (o1.getDate() == null && o2.getDate() == null)
                return 0;
            if (o1.getDate() == null)
                return 1;
            if (o2.getDate() == null)
                return -1;
            return o2.getDate().compareTo(o1.getDate());
        });

        // Pass filter values back to retain in form
        model.addAttribute("searchVal", search != null ? search : "");
        model.addAttribute("statusVal", status != null ? status : "");
        model.addAttribute("dateFromVal", dateFrom != null ? dateFrom : "");
        model.addAttribute("dateToVal", dateTo != null ? dateTo : "");
        model.addAttribute("ordersList", orders);
        return "adminOrderManagment";
    }

    @GetMapping("/adminProducts")
    public String adminProducts(Model model) {
        List<Product> products = productRepository.findAll();
        java.util.Set<String> categories = new java.util.TreeSet<>();
        java.util.Set<String> artisans = new java.util.TreeSet<>();
        for (Product p : products) {
            if ("hidden".equalsIgnoreCase(p.getStatus())) {
                // Keep it hidden!
            } else if (p.getStockQuantity() <= 0) {
                if (!"out of stock".equalsIgnoreCase(p.getStatus())) {
                    p.setStatus("out of stock");
                    productRepository.save(p);
                }
            } else {
                if (!"active".equalsIgnoreCase(p.getStatus())) {
                    p.setStatus("active");
                    productRepository.save(p);
                }
            }
            if (p.getCategory() != null && !p.getCategory().trim().isEmpty()) {
                categories.add(p.getCategory().trim());
            }
            if (p.getArtisanName() != null && !p.getArtisanName().trim().isEmpty()) {
                artisans.add(p.getArtisanName().trim());
            }
        }
        model.addAttribute("productsList", products);
        model.addAttribute("categoriesList", categories);
        model.addAttribute("artisansList", artisans);
        return "adminProducts";
    }

    @PostMapping("/admin/delete-product/{id}")
    @org.springframework.transaction.annotation.Transactional
    public String deleteProduct(@PathVariable Long id) {
        productRepository.findById(id).ifPresent(product -> {
            // Delete associated wishlist items
            var wishlistItems = wishlistItemRepository.findByProduct(product);
            wishlistItemRepository.deleteAll(wishlistItems);

            // Delete associated auctions and their bids
            var auctions = auctionRepository.findAll().stream()
                    .filter(a -> a.getProduct() != null && a.getProduct().getId().equals(id))
                    .toList();
            for (var a : auctions) {
                var bids = bidRepository.findByAuctionIdOrderByBidTimeDesc(a.getId());
                bidRepository.deleteAll(bids);
            }
            auctionRepository.deleteAll(auctions);

            // Delete associated reviews
            var reviews = reviewRepository.findByProductIdOrderByDateDesc(id);
            reviewRepository.deleteAll(reviews);

            // Set product to null in associated order items
            var orderItems = orderItemRepository.findAll().stream()
                    .filter(oi -> oi.getProduct() != null && oi.getProduct().getId().equals(id))
                    .toList();
            for (var oi : orderItems) {
                oi.setProduct(null);
                orderItemRepository.save(oi);
            }
        });
        productRepository.deleteById(id);
        return "redirect:/adminProducts";
    }

    @GetMapping("/adminRefund")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public String adminRefund(Model model) {
        List<com.example.demo.model.OrderItem> refundItems = orderItemRepository.findAllWithOrderAndProduct().stream()
                .filter(com.example.demo.model.OrderItem::isRefundRequested)
                .toList();

        long pendingReview = refundItems.stream()
                .filter(item -> "PENDING".equalsIgnoreCase(item.getAdminRefundStatus()))
                .count();

        long escalatedByArtisan = refundItems.stream()
                .filter(item -> "ESCALATED".equalsIgnoreCase(item.getRefundStatus())
                        && "PENDING".equalsIgnoreCase(item.getAdminRefundStatus()))
                .count();

        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate weekStart = today.with(java.time.DayOfWeek.MONDAY);
        LocalDate lastWeekEnd = weekStart.minusDays(1);
        LocalDate lastWeekStart = lastWeekEnd.minusDays(6);

        long approvedThisMonth = countRefundsByAdminStatusInRange(refundItems, "APPROVED", monthStart, today);
        long rejectedThisMonth = countRefundsByAdminStatusInRange(refundItems, "REJECTED", monthStart, today);

        long approvedThisWeek = countRefundsByAdminStatusInRange(refundItems, "APPROVED", weekStart, today);
        long approvedLastWeek = countRefundsByAdminStatusInRange(refundItems, "APPROVED", lastWeekStart, lastWeekEnd);
        long rejectedThisWeek = countRefundsByAdminStatusInRange(refundItems, "REJECTED", weekStart, today);
        long rejectedLastWeek = countRefundsByAdminStatusInRange(refundItems, "REJECTED", lastWeekStart, lastWeekEnd);

        double approvedWeekChange = percentChange(approvedThisWeek, approvedLastWeek);
        double rejectedWeekChange = percentChange(rejectedThisWeek, rejectedLastWeek);

        model.addAttribute("refundItems", refundItems);
        model.addAttribute("pendingCount", pendingReview);
        model.addAttribute("escalatedCount", escalatedByArtisan);
        model.addAttribute("approvedCount", approvedThisMonth);
        model.addAttribute("rejectedCount", rejectedThisMonth);
        model.addAttribute("approvedWeekChange", approvedWeekChange);
        model.addAttribute("rejectedWeekChange", rejectedWeekChange);
        model.addAttribute("approvedWeekUp", approvedWeekChange >= 0);
        model.addAttribute("rejectedWeekUp", rejectedWeekChange >= 0);
        model.addAttribute("approvedWeekChangeLabel", formatWeekChangeLabel(approvedWeekChange));
        model.addAttribute("rejectedWeekChangeLabel", formatWeekChangeLabel(rejectedWeekChange));

        return "adminRefund";
    }

    private String formatWeekChangeLabel(double changePercent) {
        if (changePercent > 0) {
            return String.format("+%.1f%% this week", changePercent);
        }
        if (changePercent < 0) {
            return String.format("%.1f%% this week", changePercent);
        }
        return "0% this week";
    }

    private java.time.LocalDate refundProcessedDate(com.example.demo.model.OrderItem item) {
        if (item.getAdminRefundProcessedAt() != null) {
            return item.getAdminRefundProcessedAt().toLocalDate();
        }
        if (item.getOrder() != null && item.getOrder().getDate() != null) {
            return item.getOrder().getDate();
        }
        return null;
    }

    private long countRefundsByAdminStatusInRange(
            List<com.example.demo.model.OrderItem> items,
            String adminStatus,
            LocalDate start,
            LocalDate end) {
        return items.stream()
                .filter(item -> adminStatus.equalsIgnoreCase(item.getAdminRefundStatus()))
                .filter(item -> {
                    java.time.LocalDate processed = refundProcessedDate(item);
                    return processed != null && !processed.isBefore(start) && !processed.isAfter(end);
                })
                .count();
    }

    private double percentChange(long current, long previous) {
        if (previous == 0) {
            return current > 0 ? 100.0 : 0.0;
        }
        return Math.round((current - previous) * 1000.0 / previous) / 10.0;
    }

    @GetMapping("/adminReports")
    public String adminReports(
            @RequestParam(value = "range", defaultValue = "month") String range,
            @RequestParam(value = "start", required = false) LocalDate start,
            @RequestParam(value = "end", required = false) LocalDate end,
            Model model) {
        Map<String, Object> report = adminReportsService.buildReport(range, start, end);
        report.forEach(model::addAttribute);
        model.addAttribute("selectedRange", range);
        model.addAttribute("report", report);
        return "adminReports";
    }

    @GetMapping("/api/admin/reports")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> adminReportsApi(
            @RequestParam(value = "range", defaultValue = "month") String range,
            @RequestParam(value = "start", required = false) LocalDate start,
            @RequestParam(value = "end", required = false) LocalDate end) {
        return ResponseEntity.ok(adminReportsService.buildReport(range, start, end));
    }

    @GetMapping("/adminReview")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public String adminReview(Model model) {
        List<com.example.demo.model.Review> reviews = reviewRepository.findAllWithProductAndCustomer();
        boolean needsSave = false;
        for (com.example.demo.model.Review r : reviews) {
            if (r.getStatus() == null || "pending".equalsIgnoreCase(r.getStatus())
                    || "approved".equalsIgnoreCase(r.getStatus())) {
                r.setStatus("active");
                needsSave = true;
            }
            if (r.getAutoFlagged() == null) {
                r.setAutoFlagged(false);
                needsSave = true;
            }
        }
        if (needsSave) {
            reviewRepository.saveAll(reviews);
        }
        model.addAttribute("reviewsList", reviews);
        return "adminReview";
    }

    @PostMapping("/admin/review/{id}/approve")
    @ResponseBody
    public ResponseEntity<?> approveReview(@PathVariable Long id) {
        return reviewRepository.findById(id).map(review -> {
            review.setStatus("approved");
            reviewRepository.save(review);
            return ResponseEntity.ok(Map.of("success", true));
        }).orElse(ResponseEntity.status(404).body(Map.of("success", false)));
    }

    @PostMapping("/admin/review/{id}/reject")
    @ResponseBody
    public ResponseEntity<?> rejectReview(@PathVariable Long id, @RequestParam String reason) {
        return reviewRepository.findById(id).map(review -> {
            review.setStatus("rejected");
            review.setRejectionReason(reason);
            reviewRepository.save(review);
            return ResponseEntity.ok(Map.of("success", true));
        }).orElse(ResponseEntity.status(404).body(Map.of("success", false)));
    }

    @PostMapping("/admin/review/approveAll")
    @ResponseBody
    public ResponseEntity<?> approveAllReviews() {
        List<com.example.demo.model.Review> reviews = reviewRepository.findAll();
        int count = 0;
        for (com.example.demo.model.Review review : reviews) {
            if ("pending".equals(review.getStatus())) {
                review.setStatus("approved");
                count++;
            }
        }
        reviewRepository.saveAll(reviews);
        return ResponseEntity.ok(Map.of("success", true, "count", count));
    }

    @GetMapping("/adminUsersManagement")
    public String adminUsersManagement(Model model) {
        List<User> users = userRepository.findAll();
        boolean needsSave = false;
        for (User u : users) {
            if (u.getStatus() == null) {
                if ("ARTISAN".equalsIgnoreCase(u.getRole())) {
                    u.setStatus("pending");
                } else {
                    u.setStatus("active");
                }
                needsSave = true;
            }
            if (u.getJoinDate() == null) {
                u.setJoinDate(java.time.LocalDateTime.now());
                needsSave = true;
            }
        }
        if (needsSave) {
            userRepository.saveAll(users);
        }
        List<User> nonAdminUsers = users.stream()
                .filter(u -> !"ADMIN".equalsIgnoreCase(u.getRole()))
                .toList();
        model.addAttribute("usersList", nonAdminUsers);
        return "adminUsersManagement";
    }

    @PostMapping("/approve-artisan/{id}")
    @org.springframework.transaction.annotation.Transactional
    public String approveArtisan(@PathVariable Long id) {
        userRepository.findById(id).ifPresent(user -> {
            user.setStatus("active");
            userRepository.save(user);
        });
        return "redirect:/adminArtisanApproval";
    }

    @PostMapping("/reject-artisan/{id}")
    @org.springframework.transaction.annotation.Transactional
    public String rejectArtisan(@PathVariable Long id) {
        userRepository.findById(id).ifPresent(user -> {
            user.setStatus("rejected");
            userRepository.save(user);
        });
        return "redirect:/adminArtisanApproval";
    }

    @PostMapping("/reconsider-artisan/{id}")
    @org.springframework.transaction.annotation.Transactional
    public String reconsiderArtisan(@PathVariable Long id) {
        userRepository.findById(id).ifPresent(user -> {
            user.setStatus("pending approval");
            userRepository.save(user);
        });
        return "redirect:/adminArtisanApproval";
    }

    @PostMapping("/suspend-artisan/{id}")
    @org.springframework.transaction.annotation.Transactional
    public String suspendArtisan(@PathVariable Long id) {
        userRepository.findById(id).ifPresent(user -> {
            user.setStatus("suspended");
            userRepository.save(user);
        });
        return "redirect:/adminArtisanApproval";
    }

    @PostMapping("/suspend-user/{id}")
    public String suspendUser(@PathVariable Long id) {
        userRepository.findById(id).ifPresent(user -> {
            user.setStatus("suspended");
            userRepository.save(user);
        });
        return "redirect:/adminUsersManagement";
    }

    @PostMapping("/api/admin/users/{id}/status")
    @ResponseBody
    public ResponseEntity<?> updateUserStatus(@PathVariable Long id, @RequestParam String status) {
        return userRepository.findById(id).map(user -> {
            user.setStatus(status.toLowerCase());
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("success", true, "message", "User status updated to " + status));
        }).orElse(ResponseEntity.status(404).body(Map.of("success", false, "message", "User not found")));
    }

    @PostMapping("/api/admin/users/{id}/update")
    @ResponseBody
    public ResponseEntity<?> updateUserDetails(@PathVariable Long id, @RequestParam String name,
            @RequestParam String email) {
        return userRepository.findById(id).map(user -> {
            user.setName(name);
            user.setEmail(email);
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("success", true, "message", "User updated successfully"));
        }).orElse(ResponseEntity.status(404).body(Map.of("success", false, "message", "User not found")));
    }

    @PostMapping("/api/admin/users/{id}/delete")
    @ResponseBody
    public ResponseEntity<?> deleteUserAccount(@PathVariable Long id) {
        return userRepository.findById(id).map(user -> {
            userRepository.delete(user);
            return ResponseEntity.ok(Map.of("success", true, "message", "User deleted successfully"));
        }).orElse(ResponseEntity.status(404).body(Map.of("success", false, "message", "User not found")));
    }

    public static class RecentActivity implements Comparable<RecentActivity> {
        private String type;
        private String title;
        private String timeAgo;
        private java.time.LocalDateTime dateTime;
        private String iconClass;
        private String bgClass;
        private String link;

        public RecentActivity(String type, String title, java.time.LocalDateTime dateTime, String iconClass,
                String bgClass, String link) {
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

        public java.time.LocalDateTime getDateTime() {
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
            if (this.dateTime == null && o.dateTime == null)
                return 0;
            if (this.dateTime == null)
                return 1;
            if (o.dateTime == null)
                return -1;
            return o.dateTime.compareTo(this.dateTime); // descending order
        }

        private static String formatTimeAgo(java.time.LocalDateTime dt) {
            if (dt == null)
                return "Recent";
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            long seconds = java.time.temporal.ChronoUnit.SECONDS.between(dt, now);
            if (seconds < 0)
                return "Just now";
            if (seconds < 60)
                return seconds + " seconds ago";
            long minutes = seconds / 60;
            if (minutes < 60)
                return minutes + " minutes ago";
            long hours = minutes / 60;
            if (hours < 24)
                return hours + " hours ago";
            long days = hours / 24;
            if (days < 30)
                return days + " days ago";
            long months = days / 30;
            return months + " months ago";
        }
    }
}
