package com.example.demo.service;

import com.example.demo.model.Address;
import com.example.demo.model.Customer;
import com.example.demo.model.Order;
import com.example.demo.model.Auction;
import com.example.demo.model.OrderItem;
import com.example.demo.model.Payment;
import com.example.demo.model.Product;
import com.example.demo.repository.AddressRepository;
import com.example.demo.repository.AuctionRepository;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.repository.OrderItemRepository;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CartService {

    public static final String STATUS_CART = "cart";
    public static final String STATUS_PENDING = "pending";
    private static final double SHIPPING_COST = 2.0;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private AuctionService auctionService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private NotificationService notificationService;

    @Transactional(readOnly = true)
    public Map<String, Object> getCartPayload(String email) {
        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));
        Optional<Order> cartOpt = orderRepository.findByCustomerAndStatusIgnoreCase(customer, STATUS_CART);
        if (cartOpt.isEmpty()) {
            return emptyCartResponse(null);
        }
        Order cart = cartOpt.get();
        return buildCartResponse(cart.getOrderId());
    }

    @Transactional
    public Map<String, Object> addItem(String email, Long productId, int quantity) {
        if (quantity < 1) {
            quantity = 1;
        }
        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        if (product.isAuctionItem()) {
            Auction auction = auctionRepository.findByProduct_Id(productId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Auction item cannot be added to the cart at this time"));
            String auctionStatus = auctionService.resolveDisplayStatus(auction);
            if (!AuctionService.DISPLAY_ENDED.equals(auctionStatus)) {
                throw new IllegalArgumentException(
                        "Auction items can only be added to the cart after the auction ends.");
            }
            Customer winner = auction.getHighestBidder();
            if (winner == null || !winner.getUserId().equals(customer.getUserId())) {
                throw new IllegalArgumentException("Only the winning bidder can add this auction item to the cart.");
            }
        }
        if (product.getStockQuantity() < 1 || "out of stock".equalsIgnoreCase(product.getStatus())) {
            throw new IllegalArgumentException("Product is out of stock");
        }

        Order cart = orderRepository.findByCustomerAndStatusIgnoreCase(customer, STATUS_CART)
                .orElseGet(() -> createCartOrder(customer));

        Optional<OrderItem> existing = orderItemRepository.findByOrderAndProduct_Id(cart, productId);
        if (existing.isPresent()) {
            OrderItem line = existing.get();
            int newQty = line.getQuantity() + quantity;
            if (newQty > product.getStockQuantity()) {
                newQty = product.getStockQuantity();
            }
            line.setQuantity(newQty);
            line.setPrice(product.getPrice());
            orderItemRepository.save(line);
        } else {
            int qty = Math.min(quantity, product.getStockQuantity());
            OrderItem line = new OrderItem();
            line.setOrder(cart);
            line.setProduct(product);
            line.setQuantity(qty);
            line.setPrice(product.getPrice());
            orderItemRepository.save(line);
        }

        refreshOrderTotal(cart.getOrderId());
        return buildCartResponse(cart.getOrderId());
    }

    @Transactional
    public Map<String, Object> updateLineQuantity(String email, Long orderItemId, int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }
        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));
        OrderItem line = orderItemRepository.findByOrderItemId(orderItemId)
                .orElseThrow(() -> new IllegalArgumentException("Cart line not found"));
        Order cart = line.getOrder();
        if (!STATUS_CART.equalsIgnoreCase(cart.getStatus())) {
            throw new IllegalArgumentException("Not a cart order");
        }
        if (cart.getCustomer() == null || !cart.getCustomer().getUserId().equals(customer.getUserId())) {
            throw new IllegalArgumentException("Forbidden");
        }
        Product product = productRepository.findById(line.getProduct().getId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        int capped = Math.min(quantity, product.getStockQuantity());
        line.setQuantity(capped);
        line.setPrice(product.getPrice());
        orderItemRepository.save(line);
        refreshOrderTotal(cart.getOrderId());
        return buildCartResponse(cart.getOrderId());
    }

    @Transactional
    public Map<String, Object> removeLine(String email, Long orderItemId) {
        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));
        OrderItem line = orderItemRepository.findByOrderItemId(orderItemId)
                .orElseThrow(() -> new IllegalArgumentException("Cart line not found"));
        Order cart = line.getOrder();
        if (!STATUS_CART.equalsIgnoreCase(cart.getStatus())) {
            throw new IllegalArgumentException("Not a cart order");
        }
        if (cart.getCustomer() == null || !cart.getCustomer().getUserId().equals(customer.getUserId())) {
            throw new IllegalArgumentException("Forbidden");
        }
        Long orderId = cart.getOrderId();
        orderItemRepository.delete(line);
        List<OrderItem> remaining = orderItemRepository.findByOrderIdWithProduct(orderId);
        if (remaining.isEmpty()) {
            orderRepository.deleteById(orderId);
            return emptyCartResponse(null);
        }
        refreshOrderTotal(orderId);
        return buildCartResponse(orderId);
    }

    @Transactional
    public Map<String, Object> checkout(String email, String paymentMethod, String deliveryMode, Long addressId) {
        if (paymentMethod == null || paymentMethod.isBlank()) {
            throw new IllegalArgumentException("Payment method is required");
        }
        boolean pickup = "pickup".equalsIgnoreCase(deliveryMode);
        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));
        Order cart = orderRepository.findByCustomerAndStatusIgnoreCase(customer, STATUS_CART)
                .orElseThrow(() -> new IllegalArgumentException("Cart is empty"));
        List<OrderItem> lines = orderItemRepository.findByOrderIdWithProduct(cart.getOrderId());
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Cart is empty");
        }

        for (OrderItem line : lines) {
            Product product = line.getProduct();
            if ("out of stock".equalsIgnoreCase(product.getStatus())
                    || product.getStockQuantity() < line.getQuantity()) {
                throw new IllegalArgumentException(
                        "Product is out of stock or does not have enough stock: " + product.getTitle());
            }
        }

        double itemsTotal = lines.stream().mapToDouble(li -> li.getPrice() * li.getQuantity()).sum();
        double shipping = pickup ? 0.0 : SHIPPING_COST;
        double grandTotal = itemsTotal + shipping;

        String shippingAddress;
        if (pickup) {
            shippingAddress = "Store pickup";
        } else {
            if (addressId == null) {
                throw new IllegalArgumentException("Shipping address is required");
            }
            Address address = addressRepository.findById(addressId)
                    .orElseThrow(() -> new IllegalArgumentException("Address not found"));
            if (!email.equalsIgnoreCase(address.getUserEmail())) {
                throw new IllegalArgumentException("Address does not belong to this user");
            }
            shippingAddress = String.format("%s, %s, %s, %s (%s)",
                    address.getStreet(), address.getCity(), address.getZip(), address.getCountry(), address.getLabel());
        }

        for (OrderItem line : lines) {
            Product product = productRepository.findById(line.getProduct().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found"));
            int oldStock = product.getStockQuantity();
            String oldStatus = product.getStatus();
            boolean wasAvailable = (oldStock > 0 && !"out of stock".equalsIgnoreCase(oldStatus));

            int newStock = product.getStockQuantity() - line.getQuantity();
            if (newStock < 0) {
                newStock = 0;
            }
            product.setStockQuantity(newStock);
            if (newStock == 0) {
                product.setStatus("out of stock");
            }
            productRepository.save(product);

            boolean isAvailable = (product.getStockQuantity() > 0
                    && !"out of stock".equalsIgnoreCase(product.getStatus()));
            boolean becameOutOfStock = (wasAvailable && !isAvailable);

            if (becameOutOfStock && product.getArtisan() != null) {
                notificationService.sendNotification(
                        product.getArtisan(),
                        "Product Out of Stock",
                        "Your product '" + product.getTitle() + "' has become out of stock.",
                        "OUT_OF_STOCK",
                        "/artisanProducts");
            }
        }

        cart.setStatus(STATUS_PENDING);
        cart.setShippingAddress(shippingAddress);
        cart.setTotalAmount(grandTotal);
        cart.setDate(LocalDate.now());

        Payment payment = new Payment();
        payment.setOrder(cart);
        payment.setAmount(grandTotal);
        payment.setPaymentMethod(paymentMethod.trim());
        payment.setStatus("completed");
        payment.setDate(LocalDate.now());
        cart.setPayment(payment);
        orderRepository.save(cart);

        // Notify artisans about received orders
        lines.stream()
                .map(line -> line.getProduct().getArtisan())
                .filter(java.util.Objects::nonNull)
                .distinct()
                .forEach(artisan -> {
                    String title = "New Order Received";
                    String msg = "You have received a new order #" + cart.getOrderId() + " containing your products.";
                    notificationService.sendNotification(
                            artisan,
                            title,
                            msg,
                            "ORDER",
                            "/artisanOrders?id=" + artisan.getUserId());
                });

        // Send order confirmation notification
        notificationService.sendNotification(
                customer,
                "Order Confirmed!",
                "Your order #" + cart.getOrderId() + " has been confirmed and is being processed.",
                "ORDER_CONFIRM",
                "/orders");

        int orders = customer.getTotalOrders() != null ? customer.getTotalOrders() : 0;
        double spent = customer.getTotalSpent() != null ? customer.getTotalSpent() : 0.0;
        customer.setTotalOrders(orders + 1);
        customer.setTotalSpent(spent + grandTotal);
        customerRepository.save(customer);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("orderId", cart.getOrderId());
        result.put("totalAmount", grandTotal);
        result.put("totalOrders", customer.getTotalOrders());
        result.put("totalSpent", customer.getTotalSpent());
        return result;
    }

    private Order createCartOrder(Customer customer) {
        Order o = new Order();
        o.setCustomer(customer);
        o.setStatus(STATUS_CART);
        o.setDate(LocalDate.now());
        o.setTotalAmount(0);
        o.setShippingAddress("");
        return orderRepository.save(o);
    }

    private void refreshOrderTotal(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        List<OrderItem> lines = orderItemRepository.findByOrderIdWithProduct(orderId);
        double sum = lines.stream().mapToDouble(li -> li.getPrice() * li.getQuantity()).sum();
        order.setTotalAmount(sum);
        orderRepository.save(order);
    }

    private Map<String, Object> emptyCartResponse(Long orderId) {
        Map<String, Object> m = new HashMap<>();
        m.put("success", true);
        m.put("orderId", orderId);
        m.put("itemCount", 0);
        m.put("subtotal", 0.0);
        m.put("items", List.of());
        return m;
    }

    private Map<String, Object> buildCartResponse(Long orderId) {
        List<OrderItem> lines = orderItemRepository.findByOrderIdWithProduct(orderId);
        if (lines.isEmpty()) {
            orderRepository.deleteById(orderId);
            return emptyCartResponse(null);
        }
        int itemCount = lines.stream().mapToInt(OrderItem::getQuantity).sum();
        double subtotal = lines.stream().mapToDouble(li -> li.getPrice() * li.getQuantity()).sum();
        List<Map<String, Object>> items = new ArrayList<>();
        for (OrderItem li : lines) {
            Product p = li.getProduct();
            Map<String, Object> row = new HashMap<>();
            row.put("orderItemId", li.getOrderItemId());
            row.put("productId", p.getId());
            row.put("title", p.getTitle());
            row.put("imageUrl", p.getImageUrl());
            row.put("unitPrice", li.getPrice());
            row.put("quantity", li.getQuantity());
            row.put("subtotal", li.getPrice() * li.getQuantity());
            row.put("maxQuantity", p.getStockQuantity());
            items.add(row);
        }
        Map<String, Object> m = new HashMap<>();
        m.put("success", true);
        m.put("orderId", orderId);
        m.put("itemCount", itemCount);
        m.put("subtotal", subtotal);
        m.put("items", items);
        return m;
    }
}
