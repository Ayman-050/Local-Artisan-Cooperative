package com.example.demo.controller;

import com.example.demo.model.Customer;
import com.example.demo.model.Order;
import com.example.demo.model.OrderItem;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.ReviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private com.example.demo.repository.OrderItemRepository orderItemRepository;

    @Autowired
    private com.example.demo.service.NotificationService notificationService;

    @Autowired
    private com.example.demo.repository.UserRepository userRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> getMyOrders(@RequestHeader(value = "X-User-Email", required = false) String email) {
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Missing X-User-Email"));
        }

        Optional<Customer> customerOpt = customerRepository.findByEmail(email.trim());
        if (customerOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Customer not found"));
        }

        Customer customer = customerOpt.get();
        List<Order> orders = orderRepository.findByCustomerAndStatusNotIgnoreCaseOrderByDateDesc(customer, "cart");

        List<Map<String, Object>> response = new ArrayList<>();
        for (Order order : orders) {
            Map<String, Object> orderMap = new HashMap<>();
            orderMap.put("orderId", order.getOrderId());
            orderMap.put("date", order.getDate().toString());
            orderMap.put("status", order.getStatus());
            orderMap.put("totalAmount", order.getTotalAmount());
            orderMap.put("shippingAddress", order.getShippingAddress());

            List<Map<String, Object>> items = new ArrayList<>();
            if (order.getOrderItems() != null) {
                for (OrderItem item : order.getOrderItems()) {
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
                    itemMap.put("reviewed",
                            item.getProduct() != null && reviewRepository
                                    .findByProductIdAndCustomerEmail(item.getProduct().getId(), customer.getEmail())
                                    .isPresent());
                    items.add(itemMap);
                }
            }
            orderMap.put("items", items);
            response.add(orderMap);
        }
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{orderId}/status")
    public ResponseEntity<?> updateOrderStatus(@PathVariable Long orderId, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        if (status == null)
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Status is required"));

        return orderRepository.findById(orderId).map(order -> {
            order.setStatus(status.toLowerCase());
            orderRepository.save(order);

            String title = "";
            String message = "";
            String type = "";

            if ("shipped".equalsIgnoreCase(status)) {
                title = "Order Shipped!";
                message = "Your order #" + order.getOrderId() + " has been shipped.";
                type = "ORDER_SHIPPED";
            } else if ("delivered".equalsIgnoreCase(status)) {
                title = "Order Delivered!";
                message = "Your order #" + order.getOrderId() + " has been delivered. Enjoy your purchase!";
                type = "ORDER_DELIVERED";
            }

            if (!title.isEmpty()) {
                notificationService.sendNotification(order.getCustomer(), title, message, type, "/orders");
            }

            return ResponseEntity.ok(Map.of("success", true));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/items/{orderItemId}/refund")
    @Transactional
    public ResponseEntity<?> requestRefund(
            @PathVariable Long orderItemId,
            @RequestBody Map<String, Object> payload) {

        Optional<OrderItem> itemOpt = orderItemRepository.findById(orderItemId);
        if (itemOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Order item not found"));
        }

        OrderItem item = itemOpt.get();
        item.setRefundRequested(true);
        item.setRefundStatus("PENDING");
        item.setAdminRefundStatus("PENDING");
        item.setRefundReason((String) payload.get("reason"));
        item.setRefundImages((String) payload.get("images"));
        orderItemRepository.save(item);

        // Notify the artisan
        if (item.getProduct() != null && item.getProduct().getArtisan() != null) {
            String title = "Refund Request Received";
            String msg = "A refund has been requested for item: " + item.getProduct().getTitle() +
                    " (Order #" + item.getOrder().getOrderId() + ").";
            notificationService.sendNotification(
                    item.getProduct().getArtisan(),
                    title,
                    msg,
                    "REFUND_REQUESTED",
                    "/artisanOrderDetail?orderId=" + item.getOrder().getOrderId() + "&id="
                            + item.getProduct().getArtisan().getUserId());
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Refund request submitted successfully."));
    }

    @PutMapping("/items/{orderItemId}/refund/decision")
    @Transactional
    public ResponseEntity<?> handleRefundDecision(
            @PathVariable Long orderItemId,
            @RequestBody Map<String, String> payload) {

        String decision = payload.get("decision"); // ACCEPT, DECLINE, ESCALATE
        if (decision == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Decision is required"));
        }

        Optional<OrderItem> itemOpt = orderItemRepository.findById(orderItemId);
        if (itemOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Order item not found"));
        }

        OrderItem item = itemOpt.get();
        String status = decision.toUpperCase();
        String msg = "";

        if ("ACCEPT".equals(status)) {
            item.setRefundStatus("APPROVED");
            item.setAdminRefundStatus("PENDING");
            msg = "Your refund request for " + item.getProduct().getTitle()
                    + " has been accepted by the artisan and is awaiting administrator approval.";
        } else if ("DECLINE".equals(status)) {
            item.setRefundStatus("DECLINED");
            item.setAdminRefundStatus("PENDING");
            String refusalReason = payload.get("refusalReason");
            if (refusalReason == null || refusalReason.trim().isEmpty()) {
                refusalReason = "No reason provided.";
            }
            item.setArtisanRefusalReason(refusalReason);
            msg = "Your refund request for " + item.getProduct().getTitle()
                    + " has been declined by the artisan (Reason: " + refusalReason
                    + "). The administrator will make the final decision.";
        } else if ("ESCALATE".equals(status)) {
            item.setRefundStatus("ESCALATED");
            item.setAdminRefundStatus("PENDING");
            msg = "Your refund request for " + item.getProduct().getTitle()
                    + " has been escalated to the administrator.";
        } else {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid decision"));
        }

        orderItemRepository.save(item);

        // Notify customer
        String title = "Refund Update";
        notificationService.sendNotification(
                item.getOrder().getCustomer(),
                title,
                msg,
                "REFUND_UPDATE",
                "/orders");

        // Notify Admins if declined or escalated
        if ("DECLINE".equals(status) || "ESCALATE".equals(status)) {
            String adminMsg = "Artisan " + item.getProduct().getArtisan().getName() + " has " + status.toLowerCase()
                    + "d the refund request for order #" + item.getOrder().getOrderId() + ". "
                    + ("DECLINE".equals(status) ? "Reason: " + payload.get("refusalReason") : "");
            userRepository.findAll().stream()
                    .filter(u -> "ADMIN".equalsIgnoreCase(u.getRole()))
                    .forEach(admin -> {
                        notificationService.sendNotification(
                                admin,
                                "Refund Requires Review",
                                adminMsg,
                                "REFUND_ADMIN_REVIEW",
                                "/adminRefund");
                    });
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Refund status updated."));
    }

    @PutMapping("/items/{orderItemId}/admin-refund/decision")
    @Transactional
    public ResponseEntity<?> handleAdminRefundDecision(
            @PathVariable Long orderItemId,
            @RequestBody Map<String, String> payload) {

        String decision = payload.get("decision"); // approve, reject
        String note = payload.get("note");

        if (decision == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Decision is required"));
        }

        Optional<OrderItem> itemOpt = orderItemRepository.findById(orderItemId);
        if (itemOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Order item not found"));
        }

        OrderItem item = itemOpt.get();
        String adminStatus = decision.toUpperCase();
        if ("APPROVE".equals(adminStatus)) {
            item.setAdminRefundStatus("APPROVED");
            item.setRefundStatus("APPROVED");
            item.setAdminRefundProcessedAt(java.time.LocalDateTime.now());
        } else if ("REJECT".equals(adminStatus)) {
            item.setAdminRefundStatus("REJECTED");
            item.setRefundStatus("DECLINED");
            item.setAdminRefundProcessedAt(java.time.LocalDateTime.now());
        } else {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid decision"));
        }

        item.setAdminNote(note);
        orderItemRepository.save(item);

        String displayStatus = "APPROVE".equals(adminStatus) ? "approved" : "rejected";

        // Notify customer
        String customerMsg = "Your refund request for " + item.getProduct().getTitle() + " has been " + displayStatus
                + " by the administrator. Note: " + (note != null ? note : "None");
        notificationService.sendNotification(
                item.getOrder().getCustomer(),
                "Refund " + ("APPROVE".equals(adminStatus) ? "Approved" : "Rejected"),
                customerMsg,
                "REFUND_FINAL_DECISION",
                "/orders");

        // Notify artisan
        if (item.getProduct() != null && item.getProduct().getArtisan() != null) {
            String artisanMsg = "The refund request for " + item.getProduct().getTitle() + " (Order #"
                    + item.getOrder().getOrderId() + ") has been " + displayStatus + " by the administrator. Note: "
                    + (note != null ? note : "None");
            notificationService.sendNotification(
                    item.getProduct().getArtisan(),
                    "Refund " + ("APPROVE".equals(adminStatus) ? "Approved" : "Rejected"),
                    artisanMsg,
                    "REFUND_FINAL_DECISION",
                    "/artisanOrderDetail?orderId=" + item.getOrder().getOrderId() + "&id="
                            + item.getProduct().getArtisan().getUserId());
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Admin refund status updated."));
    }
}
