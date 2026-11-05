package com.artsyvibe.controller;

import com.artsyvibe.model.Artisan;
import com.artsyvibe.model.Customer;
import com.artsyvibe.model.Order;
import com.artsyvibe.model.OrderItem;
import com.artsyvibe.model.OrderStatus;
import com.artsyvibe.model.RefundStatus;
import com.artsyvibe.model.Roles;
import com.artsyvibe.model.User;
import com.artsyvibe.repository.OrderItemRepository;
import com.artsyvibe.repository.OrderRepository;
import com.artsyvibe.repository.ReviewRepository;
import com.artsyvibe.repository.UserRepository;
import com.artsyvibe.security.CurrentUser;
import com.artsyvibe.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private static final int MAX_REFUND_TEXT = 4000;

    private final OrderRepository orderRepository;
    private final ReviewRepository reviewRepository;
    private final OrderItemRepository orderItemRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;

    /** The signed-in customer's placed orders (the open cart is excluded). */
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> getMyOrders() {
        Customer customer = currentUser.requireCustomer();
        List<Order> orders = orderRepository.findByCustomerAndStatusNotIgnoreCaseOrderByDateDesc(customer, OrderStatus.CART);

        // One query for "which of these products has the customer reviewed" instead of one per line.
        Set<Long> productIds = new HashSet<>();
        orders.forEach(o -> {
            if (o.getOrderItems() != null) {
                o.getOrderItems().forEach(i -> {
                    if (i.getProduct() != null) {
                        productIds.add(i.getProduct().getId());
                    }
                });
            }
        });
        Set<Long> reviewed = productIds.isEmpty() ? Set.of()
                : new HashSet<>(reviewRepository.findReviewedProductIds(customer.getUserId(), productIds));

        List<Map<String, Object>> response = new ArrayList<>();
        for (Order order : orders) {
            Map<String, Object> orderMap = new HashMap<>();
            orderMap.put("orderId", order.getOrderId());
            orderMap.put("date", order.getDate() != null ? order.getDate().toString() : null);
            orderMap.put("status", order.getStatus());
            orderMap.put("totalAmount", order.getTotalAmount());
            orderMap.put("shippingAddress", order.getShippingAddress());

            List<Map<String, Object>> items = new ArrayList<>();
            if (order.getOrderItems() != null) {
                for (OrderItem item : order.getOrderItems()) {
                    if (item.getProduct() == null) {
                        continue; // product was removed by an admin
                    }
                    Map<String, Object> itemMap = new HashMap<>();
                    itemMap.put("orderItemId", item.getOrderItemId());
                    itemMap.put("title", item.getProduct().getTitle());
                    itemMap.put("imageUrl", item.getProduct().getImageUrl());
                    itemMap.put("productId", item.getProduct().getId());
                    itemMap.put("quantity", item.getQuantity());
                    itemMap.put("price", item.getPrice());
                    itemMap.put("refundRequested", item.isRefundRequested());
                    itemMap.put("refundStatus", item.getRefundStatus());
                    itemMap.put("refundReason", item.getRefundReason());
                    itemMap.put("refundImages", item.getRefundImages());
                    itemMap.put("artisanRefusalReason", item.getArtisanRefusalReason());
                    itemMap.put("adminRefundStatus", item.getAdminRefundStatus());
                    itemMap.put("adminNote", item.getAdminNote());
                    itemMap.put("fulfilmentStatus", item.getFulfilmentStatus());
                    itemMap.put("reviewed", reviewed.contains(item.getProduct().getId()));
                    items.add(itemMap);
                }
            }
            orderMap.put("items", items);
            response.add(orderMap);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Artisans update the fulfilment of <em>their own</em> lines in the order; admins update every line.
     * The order's overall status is then recomputed from all of its lines.
     */
    @PutMapping("/{orderId}/status")
    @Transactional
    public ResponseEntity<?> updateOrderStatus(@PathVariable Long orderId, @RequestBody Map<String, String> body) {
        String status = body.get("status") != null ? body.get("status").trim().toLowerCase(Locale.ROOT) : null;
        if (status == null || !OrderStatus.FULFILMENT_STATUSES.contains(status)) {
            throw new IllegalArgumentException("Status must be one of " + OrderStatus.FULFILMENT_STATUSES);
        }
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (OrderStatus.CART.equalsIgnoreCase(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        User caller = currentUser.require();
        boolean isAdmin = Roles.ADMIN.equalsIgnoreCase(caller.getRole());
        if (!isAdmin && !orderItemRepository.existsByOrderAndArtisan(orderId, caller.getUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }

        List<OrderItem> lines = orderItemRepository.findByOrderIdWithArtisan(orderId);
        for (OrderItem line : lines) {
            boolean mine = line.getProduct().getArtisan() != null
                    && line.getProduct().getArtisan().getUserId().equals(caller.getUserId());
            if (isAdmin || mine) {
                line.setFulfilmentStatus(status);
            }
        }
        String previousOverall = order.getStatus();
        String overall = OrderStatus.aggregate(lines.stream().map(OrderItem::getFulfilmentStatus).toList());
        order.setStatus(overall);

        // Admin updates cover the whole order; artisan updates only cover that artisan's items.
        String subject = isAdmin
                ? "Your order #" + order.getOrderId() + " has"
                : "Items from " + shopName(caller) + " in your order #" + order.getOrderId() + " have";
        if (OrderStatus.DELIVERED.equals(overall) && !overall.equalsIgnoreCase(previousOverall)) {
            notificationService.sendNotification(order.getCustomer(), "Order Delivered!",
                    "Your order #" + order.getOrderId() + " has been delivered. Enjoy your purchase!",
                    "ORDER_DELIVERED", "/orders");
        } else if (OrderStatus.SHIPPED.equals(status)) {
            notificationService.sendNotification(order.getCustomer(), "Order Shipped!",
                    subject + " been shipped.", "ORDER_SHIPPED", "/orders");
        } else if (OrderStatus.DELIVERED.equals(status)) {
            notificationService.sendNotification(order.getCustomer(), "Items Delivered",
                    subject + " been delivered.", "ORDER_DELIVERED", "/orders");
        }
        return ResponseEntity.ok(Map.of("success", true, "orderStatus", overall, "itemStatus", status));
    }

    private static String shopName(User user) {
        if (user instanceof Artisan a && a.getShopName() != null && !a.getShopName().isBlank()) {
            return a.getShopName();
        }
        return user.getName();
    }

    /** A customer asks for a refund on one line of one of their own delivered orders. */
    @PostMapping("/items/{orderItemId}/refund")
    @Transactional
    public ResponseEntity<?> requestRefund(@PathVariable Long orderItemId, @RequestBody Map<String, Object> payload) {
        Customer customer = currentUser.requireCustomer();
        OrderItem item = orderItemRepository.findById(orderItemId)
                .filter(i -> i.getOrder() != null && i.getOrder().getCustomer() != null
                        && i.getOrder().getCustomer().getUserId().equals(customer.getUserId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order item not found"));

        if (!OrderStatus.DELIVERED.equalsIgnoreCase(item.getFulfilmentStatus())
                && !OrderStatus.DELIVERED.equalsIgnoreCase(item.getOrder().getStatus())) {
            throw new IllegalArgumentException("Refunds can only be requested for delivered orders.");
        }
        if (item.isRefundRequested()) {
            throw new IllegalArgumentException("A refund has already been requested for this item.");
        }
        String reason = RequestValues.optionalString(payload, "reason");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Please describe the reason for the refund.");
        }
        if (reason.length() > MAX_REFUND_TEXT) {
            throw new IllegalArgumentException("Reason is too long.");
        }

        item.setRefundRequested(true);
        item.setRefundStatus(RefundStatus.PENDING);
        item.setAdminRefundStatus(RefundStatus.PENDING);
        item.setRefundReason(reason.trim());
        item.setRefundImages(RequestValues.optionalString(payload, "images"));

        if (item.getProduct() != null && item.getProduct().getArtisan() != null) {
            notificationService.sendNotification(item.getProduct().getArtisan(), "Refund Request Received",
                    "A refund has been requested for item: " + item.getProduct().getTitle()
                            + " (Order #" + item.getOrder().getOrderId() + ").",
                    "REFUND_REQUESTED", "/artisanOrderDetail?orderId=" + item.getOrder().getOrderId());
        }
        return ResponseEntity.ok(Map.of("success", true, "message", "Refund request submitted successfully."));
    }

    /** The artisan who sold the item accepts, declines or escalates a pending refund request. */
    @PutMapping("/items/{orderItemId}/refund/decision")
    @Transactional
    public ResponseEntity<?> handleRefundDecision(@PathVariable Long orderItemId, @RequestBody Map<String, String> payload) {
        Long artisanId = currentUser.requireArtisan().getUserId();
        OrderItem item = orderItemRepository.findById(orderItemId)
                .filter(i -> i.getProduct() != null && i.getProduct().getArtisan() != null
                        && i.getProduct().getArtisan().getUserId().equals(artisanId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order item not found"));

        if (!item.isRefundRequested() || !RefundStatus.PENDING.equalsIgnoreCase(item.getRefundStatus())) {
            throw new IllegalArgumentException("This refund request is not awaiting your decision.");
        }
        String decision = payload.get("decision") != null ? payload.get("decision").toUpperCase(Locale.ROOT) : "";
        String productTitle = item.getProduct().getTitle();
        String refusalReason = payload.get("refusalReason");
        if (refusalReason == null || refusalReason.isBlank()) {
            refusalReason = "No reason provided.";
        } else if (refusalReason.length() > MAX_REFUND_TEXT) {
            throw new IllegalArgumentException("Reason is too long.");
        }

        String msg;
        switch (decision) {
            case "ACCEPT" -> {
                item.setRefundStatus(RefundStatus.APPROVED);
                msg = "Your refund request for " + productTitle
                        + " has been accepted by the artisan and is awaiting administrator approval.";
            }
            case "DECLINE" -> {
                item.setRefundStatus(RefundStatus.DECLINED);
                item.setArtisanRefusalReason(refusalReason);
                msg = "Your refund request for " + productTitle + " has been declined by the artisan (Reason: "
                        + refusalReason + "). The administrator will make the final decision.";
            }
            case "ESCALATE" -> {
                item.setRefundStatus(RefundStatus.ESCALATED);
                msg = "Your refund request for " + productTitle + " has been escalated to the administrator.";
            }
            default -> throw new IllegalArgumentException("Invalid decision");
        }
        item.setAdminRefundStatus(RefundStatus.PENDING);

        notificationService.sendNotification(item.getOrder().getCustomer(), "Refund Update", msg, "REFUND_UPDATE", "/orders");

        if (!"ACCEPT".equals(decision)) {
            String adminMsg = "Artisan " + item.getProduct().getArtisan().getName() + " has "
                    + decision.toLowerCase(Locale.ROOT) + "d the refund request for order #"
                    + item.getOrder().getOrderId() + ". "
                    + ("DECLINE".equals(decision) ? "Reason: " + refusalReason : "");
            userRepository.findByRoleIgnoreCase(Roles.ADMIN).forEach(admin -> notificationService.sendNotification(
                    admin, "Refund Requires Review", adminMsg, "REFUND_ADMIN_REVIEW", "/adminRefund"));
        }
        return ResponseEntity.ok(Map.of("success", true, "message", "Refund status updated."));
    }

    /** Final admin decision on a refund. */
    @PutMapping("/items/{orderItemId}/admin-refund/decision")
    @Transactional
    public ResponseEntity<?> handleAdminRefundDecision(@PathVariable Long orderItemId,
            @RequestBody Map<String, String> payload) {
        currentUser.requireAdmin();
        OrderItem item = orderItemRepository.findById(orderItemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order item not found"));
        if (!item.isRefundRequested() || !RefundStatus.PENDING.equalsIgnoreCase(item.getAdminRefundStatus())) {
            throw new IllegalArgumentException("This refund has already been decided.");
        }

        String decision = payload.get("decision") != null ? payload.get("decision").toUpperCase(Locale.ROOT) : "";
        String note = payload.get("note");
        if (note != null && note.length() > MAX_REFUND_TEXT) {
            throw new IllegalArgumentException("Note is too long.");
        }
        boolean approve;
        switch (decision) {
            case "APPROVE" -> approve = true;
            case "REJECT" -> approve = false;
            default -> throw new IllegalArgumentException("Invalid decision");
        }

        item.setAdminRefundStatus(approve ? RefundStatus.APPROVED : RefundStatus.REJECTED);
        item.setRefundStatus(approve ? RefundStatus.APPROVED : RefundStatus.DECLINED);
        item.setAdminRefundProcessedAt(LocalDateTime.now());
        item.setAdminNote(note);

        String displayStatus = approve ? "approved" : "rejected";
        String title = "Refund " + (approve ? "Approved" : "Rejected");
        String productTitle = item.getProduct() != null ? item.getProduct().getTitle() : "your item";
        String noteText = note != null ? note : "None";

        notificationService.sendNotification(item.getOrder().getCustomer(), title,
                "Your refund request for " + productTitle + " has been " + displayStatus
                        + " by the administrator. Note: " + noteText,
                "REFUND_FINAL_DECISION", "/orders");

        if (item.getProduct() != null && item.getProduct().getArtisan() != null) {
            notificationService.sendNotification(item.getProduct().getArtisan(), title,
                    "The refund request for " + productTitle + " (Order #" + item.getOrder().getOrderId()
                            + ") has been " + displayStatus + " by the administrator. Note: " + noteText,
                    "REFUND_FINAL_DECISION", "/artisanOrderDetail?orderId=" + item.getOrder().getOrderId());
        }
        return ResponseEntity.ok(Map.of("success", true, "message", "Admin refund status updated."));
    }
}
