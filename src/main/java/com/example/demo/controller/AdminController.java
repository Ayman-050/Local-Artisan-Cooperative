package com.example.demo.controller;

import com.example.demo.model.Artisan;
import com.example.demo.model.Auction;
import com.example.demo.model.Order;
import com.example.demo.model.OrderItem;
import com.example.demo.model.Product;
import com.example.demo.model.RefundStatus;
import com.example.demo.model.Review;
import com.example.demo.model.Roles;
import com.example.demo.model.User;
import com.example.demo.model.UserStatus;
import com.example.demo.repository.ArtisanRepository;
import com.example.demo.repository.AuctionRepository;
import com.example.demo.repository.BidRepository;
import com.example.demo.repository.OrderItemRepository;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.ReviewRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.CurrentUser;
import com.example.demo.service.AccountService;
import com.example.demo.service.AdminDashboardService;
import com.example.demo.service.AdminReportsService;
import com.example.demo.service.AuctionService;
import com.example.demo.service.AuthService;
import com.example.demo.service.ProductService;
import com.example.demo.web.PageView;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Admin back office. Every route here requires ROLE_ADMIN (see SecurityConfig). GET handlers are read-only;
 * data clean-ups they used to perform on every page view now live in the V2 Flyway migration.
 */
@Controller
@RequiredArgsConstructor
public class AdminController {

    private static final Set<String> ASSIGNABLE_USER_STATUSES = Set.of(
            UserStatus.ACTIVE, UserStatus.SUSPENDED, UserStatus.REJECTED, UserStatus.PENDING);

    private final UserRepository userRepository;
    private final ArtisanRepository artisanRepository;
    private final ProductRepository productRepository;
    private final AuctionRepository auctionRepository;
    private final OrderRepository orderRepository;
    private final ReviewRepository reviewRepository;
    private final BidRepository bidRepository;
    private final OrderItemRepository orderItemRepository;
    private final AuctionService auctionService;
    private final AdminReportsService adminReportsService;
    private final AdminDashboardService adminDashboardService;
    private final ProductService productService;
    private final AccountService accountService;
    private final CurrentUser currentUser;

    @GetMapping("/adminDashboard")
    public String dashboard(Model model) {
        model.addAllAttributes(adminDashboardService.build(LocalDate.now()));
        return "adminDashboard";
    }

    @GetMapping("/adminArtisanApproval")
    public String adminArtisanApproval(Model model) {
        model.addAttribute("pendingArtisans",
                artisanRepository.findByStatusIn(List.of(UserStatus.PENDING_APPROVAL, UserStatus.PENDING)));
        model.addAttribute("approvedArtisans", artisanRepository.findByStatus(UserStatus.ACTIVE));
        model.addAttribute("rejectedArtisans", artisanRepository.findByStatus(UserStatus.REJECTED));
        return "adminArtisanApproval";
    }

    @GetMapping("/adminAuction")
    @Transactional(readOnly = true)
    public String adminAuction(Model model) {
        List<Auction> allAuctions = auctionRepository.findAllWithProduct();
        Map<Long, Long> auctionBidsMap = new LinkedHashMap<>();
        Map<Long, String> auctionDisplayStatusMap = new LinkedHashMap<>();
        Set<String> categories = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Set<String> artisans = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        // One grouped query for all bid counts (was one COUNT query per auction).
        Map<Long, Long> bidCounts = new HashMap<>();
        for (Object[] row : bidRepository.countBidsPerAuction()) {
            bidCounts.put((Long) row[0], (Long) row[1]);
        }
        for (Auction a : allAuctions) {
            auctionBidsMap.put(a.getId(), bidCounts.getOrDefault(a.getId(), 0L));
            auctionDisplayStatusMap.put(a.getId(), auctionService.resolveDisplayStatus(a).toLowerCase(Locale.ROOT));
            if (a.getProduct() != null) {
                addIfPresent(categories, a.getProduct().getCategory());
                addIfPresent(artisans, a.getProduct().getArtisanName());
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
                .map(data -> ResponseEntity.ok(Map.<String, Object>of("success", true, "data", data)))
                .orElse(ResponseEntity.status(404).body(Map.of("success", false, "message", "Auction not found")));
    }

    @PostMapping("/api/admin/auctions/{id}/extend")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> extendAuction(@PathVariable Long id,
            @RequestParam(defaultValue = "30") int minutes) {
        return result(auctionService.extendAuction(id, minutes));
    }

    @PostMapping("/api/admin/auctions/{id}/cancel")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> cancelAuction(@PathVariable Long id) {
        return result(auctionService.cancelAuction(id));
    }

    @PostMapping("/api/admin/auctions/{id}/flag")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> flagAuction(@PathVariable Long id,
            @RequestParam(required = false) String reason) {
        return result(auctionService.flagAuction(id, reason));
    }

    @GetMapping("/adminNotification")
    public String adminNotification() {
        return "adminNotification";
    }

    @GetMapping({ "/adminOrderDetail", "/adminOrderDetail/{id}" })
    public String adminOrderDetail(@PathVariable(required = false) Long id,
            @RequestParam(name = "id", required = false) Long idParam, Model model) {
        Long orderId = id != null ? id : idParam;
        if (orderId != null) {
            orderRepository.findById(orderId).ifPresent(order -> {
                model.addAttribute("order", order);
                model.addAttribute("customer", order.getCustomer());
                Artisan artisan = order.getOrderItems() == null ? null : order.getOrderItems().stream()
                        .filter(item -> item.getProduct() != null && item.getProduct().getArtisan() != null)
                        .map(item -> item.getProduct().getArtisan())
                        .findFirst().orElse(null);
                model.addAttribute("artisan", artisan);
                model.addAttribute("payment", order.getPayment());
                model.addAttribute("orderItems", order.getOrderItems());
            });
        }
        return "adminOrderDetail";
    }

    @GetMapping("/adminOrderManagment")
    public String adminOrderManagment(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Integer page,
            Model model) {
        LocalDate from = dateFrom != null ? dateFrom : LocalDate.of(1970, 1, 1);
        LocalDate to = dateTo != null ? dateTo : LocalDate.of(9999, 12, 31);
        Page<Order> orders = orderRepository.searchForAdmin(PageView.likePattern(search), PageView.filter(status),
                from, to, PageView.request(page,
                        Sort.by(Sort.Direction.DESC, "date").and(Sort.by(Sort.Direction.DESC, "orderId"))));

        model.addAttribute("pager", PageView.of(orders));
        model.addAttribute("searchVal", search != null ? search : "");
        model.addAttribute("statusVal", status != null ? status : "");
        model.addAttribute("dateFromVal", dateFrom != null ? dateFrom.toString() : "");
        model.addAttribute("dateToVal", dateTo != null ? dateTo.toString() : "");
        model.addAttribute("ordersList", orders.getContent());
        return "adminOrderManagment";
    }

    @GetMapping("/adminProducts")
    public String adminProducts(@RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String artisan,
            @RequestParam(required = false) Integer page, Model model) {
        Page<Product> products = productRepository.searchForAdmin(PageView.likePattern(q), PageView.filter(category),
                PageView.filter(status), PageView.filter(artisan),
                PageView.request(page, Sort.by(Sort.Direction.DESC, "id")));
        model.addAttribute("productsList", products.getContent());
        model.addAttribute("pager", PageView.of(products));
        model.addAttribute("categoriesList", new TreeSet<>(productRepository.findDistinctCategories()));
        model.addAttribute("artisansList", productRepository.findDistinctArtisanNames());
        return "adminProducts";
    }

    /** Deletes a product, or hides it when it has order/bid history. */
    @PostMapping("/admin/delete-product/{id}")
    public String deleteProduct(@PathVariable Long id) {
        productRepository.findById(id).ifPresent(productService::deleteOrHide);
        return "redirect:/adminProducts";
    }

    @GetMapping("/adminRefund")
    @Transactional(readOnly = true)
    public String adminRefund(@RequestParam(required = false) Integer page, Model model) {
        Page<OrderItem> refunds = orderItemRepository.findRefundRequestsPage(PageView.request(page, Sort.unsorted()));
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDate lastWeekEnd = weekStart.minusDays(1);
        LocalDate lastWeekStart = lastWeekEnd.minusDays(6);

        double approvedWeekChange = percentChange(
                countDecided(RefundStatus.APPROVED, weekStart, today),
                countDecided(RefundStatus.APPROVED, lastWeekStart, lastWeekEnd));
        double rejectedWeekChange = percentChange(
                countDecided(RefundStatus.REJECTED, weekStart, today),
                countDecided(RefundStatus.REJECTED, lastWeekStart, lastWeekEnd));

        model.addAttribute("refundItems", refunds.getContent());
        model.addAttribute("pager", PageView.of(refunds));
        model.addAttribute("pendingCount",
                orderItemRepository.countByRefundRequestedTrueAndAdminRefundStatusIgnoreCase(RefundStatus.PENDING));
        model.addAttribute("escalatedCount", orderItemRepository
                .countByRefundRequestedTrueAndRefundStatusIgnoreCaseAndAdminRefundStatusIgnoreCase(
                        RefundStatus.ESCALATED, RefundStatus.PENDING));
        model.addAttribute("approvedCount", countDecided(RefundStatus.APPROVED, monthStart, today));
        model.addAttribute("rejectedCount", countDecided(RefundStatus.REJECTED, monthStart, today));
        model.addAttribute("approvedWeekChange", approvedWeekChange);
        model.addAttribute("rejectedWeekChange", rejectedWeekChange);
        model.addAttribute("approvedWeekUp", approvedWeekChange >= 0);
        model.addAttribute("rejectedWeekUp", rejectedWeekChange >= 0);
        model.addAttribute("approvedWeekChangeLabel", formatWeekChangeLabel(approvedWeekChange));
        model.addAttribute("rejectedWeekChangeLabel", formatWeekChangeLabel(rejectedWeekChange));
        return "adminRefund";
    }

    @GetMapping("/adminReports")
    public String adminReports(@RequestParam(value = "range", defaultValue = "month") String range,
            @RequestParam(value = "start", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(value = "end", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
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
            @RequestParam(value = "start", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(value = "end", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ResponseEntity.ok(adminReportsService.buildReport(range, start, end));
    }

    @GetMapping("/adminReview")
    @Transactional(readOnly = true)
    public String adminReview(@RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean flagged,
            @RequestParam(required = false) Integer page, Model model) {
        String statusFilter = PageView.filter(status).toLowerCase(Locale.ROOT);
        if (!Set.of("active", "rejected").contains(statusFilter)) {
            statusFilter = "";
        }
        Page<Review> reviews = reviewRepository.searchForAdmin(PageView.likePattern(q), PageView.filter(category),
                rating != null ? rating : 0, statusFilter, Boolean.TRUE.equals(flagged),
                PageView.request(page, Sort.by(Sort.Direction.DESC, "date").and(Sort.by(Sort.Direction.DESC, "reviewId"))));
        model.addAttribute("reviewsList", reviews.getContent());
        model.addAttribute("pager", PageView.of(reviews));
        return "adminReview";
    }

    @PostMapping("/admin/review/{id}/approve")
    @ResponseBody
    @Transactional
    public ResponseEntity<?> approveReview(@PathVariable Long id) {
        return reviewRepository.findById(id).map(review -> {
            review.setStatus("active");
            review.setRejectionReason(null);
            return ResponseEntity.ok(Map.of("success", true));
        }).orElse(ResponseEntity.status(404).body(Map.of("success", false)));
    }

    @PostMapping("/admin/review/{id}/reject")
    @ResponseBody
    @Transactional
    public ResponseEntity<?> rejectReview(@PathVariable Long id, @RequestParam String reason) {
        if (reason.length() > 255) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Reason is too long."));
        }
        return reviewRepository.findById(id).map(review -> {
            review.setStatus("rejected");
            review.setRejectionReason(reason);
            return ResponseEntity.ok(Map.of("success", true));
        }).orElse(ResponseEntity.status(404).body(Map.of("success", false)));
    }

    /** Clears the auto-flag on every flagged review that has not been rejected. */
    @PostMapping("/admin/review/approveAll")
    @ResponseBody
    @Transactional
    public ResponseEntity<?> approveAllReviews() {
        int count = 0;
        for (Review review : reviewRepository.findAll()) {
            if (Boolean.TRUE.equals(review.getAutoFlagged()) && !"rejected".equalsIgnoreCase(review.getStatus())) {
                review.setAutoFlagged(false);
                review.setStatus("active");
                count++;
            }
        }
        return ResponseEntity.ok(Map.of("success", true, "count", count));
    }

    @GetMapping("/adminUsersManagement")
    public String adminUsersManagement(@RequestParam(required = false) String q,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page, Model model) {
        Page<User> users = userRepository.searchForAdmin(PageView.likePattern(q), PageView.filter(role),
                PageView.filter(status), PageView.request(page, Sort.by(Sort.Direction.DESC, "joinDate")));
        model.addAttribute("usersList", users.getContent());
        model.addAttribute("pager", PageView.of(users));
        return "adminUsersManagement";
    }

    @PostMapping("/approve-artisan/{id}")
    @Transactional
    public String approveArtisan(@PathVariable Long id) {
        artisanRepository.findById(id).ifPresent(a -> a.setStatus(UserStatus.ACTIVE));
        return "redirect:/adminArtisanApproval";
    }

    @PostMapping("/reject-artisan/{id}")
    @Transactional
    public String rejectArtisan(@PathVariable Long id) {
        artisanRepository.findById(id).ifPresent(a -> a.setStatus(UserStatus.REJECTED));
        return "redirect:/adminArtisanApproval";
    }

    @PostMapping("/reconsider-artisan/{id}")
    @Transactional
    public String reconsiderArtisan(@PathVariable Long id) {
        artisanRepository.findById(id).ifPresent(a -> a.setStatus(UserStatus.PENDING_APPROVAL));
        return "redirect:/adminArtisanApproval";
    }

    @PostMapping("/suspend-artisan/{id}")
    @Transactional
    public String suspendArtisan(@PathVariable Long id) {
        artisanRepository.findById(id).ifPresent(a -> a.setStatus(UserStatus.SUSPENDED));
        return "redirect:/adminArtisanApproval";
    }

    @PostMapping("/suspend-user/{id}")
    @Transactional
    public String suspendUser(@PathVariable Long id) {
        findManageableUser(id).setStatus(UserStatus.SUSPENDED);
        return "redirect:/adminUsersManagement";
    }

    @PostMapping("/api/admin/users/{id}/status")
    @ResponseBody
    @Transactional
    public ResponseEntity<?> updateUserStatus(@PathVariable Long id, @RequestParam String status) {
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        if (!ASSIGNABLE_USER_STATUSES.contains(normalized)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Unknown status."));
        }
        findManageableUser(id).setStatus(normalized);
        return ResponseEntity.ok(Map.of("success", true, "message", "User status updated to " + normalized));
    }

    @PostMapping("/api/admin/users/{id}/update")
    @ResponseBody
    @Transactional
    public ResponseEntity<?> updateUserDetails(@PathVariable Long id, @RequestParam String name,
            @RequestParam String email) {
        User user = findManageableUser(id);
        String newName = name.trim();
        String newEmail = AuthService.normalizeEmail(email);
        if (newName.isEmpty() || newName.length() > 100) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid name."));
        }
        if (newEmail == null || !newEmail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid email."));
        }
        boolean emailTaken = userRepository.findByEmailIgnoreCase(newEmail)
                .filter(other -> !other.getUserId().equals(id)).isPresent();
        if (emailTaken) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Email already in use."));
        }
        user.setName(newName);
        user.setEmail(newEmail);
        return ResponseEntity.ok(Map.of("success", true, "message", "User updated successfully"));
    }

    /** Closes (anonymises) the account; see AccountService for why rows are not hard-deleted. */
    @PostMapping("/api/admin/users/{id}/delete")
    @ResponseBody
    public ResponseEntity<?> deleteUserAccount(@PathVariable Long id) {
        accountService.closeAccount(findManageableUser(id));
        return ResponseEntity.ok(Map.of("success", true, "message", "User deleted successfully"));
    }

    /** Admins manage customers and artisans, never other admins or themselves. */
    private User findManageableUser(Long id) {
        User target = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (Roles.ADMIN.equalsIgnoreCase(target.getRole())
                || currentUser.id().map(id::equals).orElse(false)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator accounts cannot be changed here.");
        }
        return target;
    }

    private static ResponseEntity<Map<String, Object>> result(Map<String, Object> result) {
        return Boolean.TRUE.equals(result.get("success")) ? ResponseEntity.ok(result)
                : ResponseEntity.badRequest().body(result);
    }

    private static void addIfPresent(Set<String> set, String value) {
        if (value != null && !value.isBlank()) {
            set.add(value.trim());
        }
    }

    private static String formatWeekChangeLabel(double changePercent) {
        if (changePercent > 0) {
            return String.format("+%.1f%% this week", changePercent);
        }
        if (changePercent < 0) {
            return String.format("%.1f%% this week", changePercent);
        }
        return "0% this week";
    }

    private long countDecided(String adminStatus, LocalDate start, LocalDate end) {
        return orderItemRepository.countDecidedRefunds(adminStatus, start.atStartOfDay(),
                end.plusDays(1).atStartOfDay().minusNanos(1), start, end);
    }

    private static double percentChange(long current, long previous) {
        if (previous == 0) {
            return current > 0 ? 100.0 : 0.0;
        }
        return Math.round((current - previous) * 1000.0 / previous) / 10.0;
    }
}
