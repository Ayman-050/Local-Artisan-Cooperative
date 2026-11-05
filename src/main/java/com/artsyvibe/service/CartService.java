package com.artsyvibe.service;

import com.artsyvibe.model.Address;
import com.artsyvibe.model.Auction;
import com.artsyvibe.model.Customer;
import com.artsyvibe.model.Order;
import com.artsyvibe.model.OrderItem;
import com.artsyvibe.model.OrderStatus;
import com.artsyvibe.model.Payment;
import com.artsyvibe.model.Product;
import com.artsyvibe.model.ProductStatus;
import com.artsyvibe.repository.AddressRepository;
import com.artsyvibe.repository.AuctionRepository;
import com.artsyvibe.repository.CustomerRepository;
import com.artsyvibe.repository.OrderItemRepository;
import com.artsyvibe.repository.OrderRepository;
import com.artsyvibe.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The cart is an {@link Order} in status {@link OrderStatus#CART}; checkout turns it into a placed order.
 * All methods take the signed-in {@link Customer} (resolved from the session by the controller).
 */
@Service
@RequiredArgsConstructor
public class CartService {

    public static final String STATUS_CART = OrderStatus.CART;
    public static final String STATUS_PENDING = OrderStatus.PENDING;
    static final BigDecimal SHIPPING_COST = new BigDecimal("2.000");
    private static final int MAX_LINE_QUANTITY = 99;

    private final CustomerRepository customerRepository;
    private final AddressRepository addressRepository;
    private final AuctionRepository auctionRepository;
    private final AuctionService auctionService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final NotificationService notificationService;
    private final ProductRepository productRepository;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public Map<String, Object> getCartPayload(Customer customer) {
        return orderRepository.findByCustomerAndStatusIgnoreCase(customer, STATUS_CART)
                .map(cart -> buildCartResponse(cart.getOrderId()))
                .orElseGet(CartService::emptyCartResponse);
    }

    @Transactional
    public Map<String, Object> addItem(Customer customer, Long productId, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        assertPurchasable(product, customer);
        if (product.isAuctionItem()) {
            quantity = 1; // an auction lot is a single item
        }
        quantity = Math.max(1, Math.min(quantity, MAX_LINE_QUANTITY));
        if (product.getStockQuantity() < 1) {
            throw new IllegalArgumentException("Product is out of stock");
        }

        Order cart = orderRepository.findByCustomerAndStatusIgnoreCase(customer, STATUS_CART)
                .orElseGet(() -> createCartOrder(customer));

        OrderItem line = orderItemRepository.findByOrderAndProduct_Id(cart, productId).orElseGet(() -> {
            OrderItem fresh = new OrderItem();
            fresh.setOrder(cart);
            fresh.setProduct(product);
            fresh.setQuantity(0);
            return fresh;
        });
        int newQty = product.isAuctionItem() ? 1 : line.getQuantity() + quantity;
        line.setQuantity(Math.min(newQty, product.getStockQuantity()));
        line.setPrice(unitPrice(product));
        orderItemRepository.save(line);

        refreshOrderTotal(cart.getOrderId());
        return buildCartResponse(cart.getOrderId());
    }

    @Transactional
    public Map<String, Object> updateLineQuantity(Customer customer, Long orderItemId, int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }
        OrderItem line = findOwnedCartLine(customer, orderItemId);
        Product product = line.getProduct();
        int max = product.isAuctionItem() ? 1 : Math.min(product.getStockQuantity(), MAX_LINE_QUANTITY);
        line.setQuantity(Math.min(quantity, Math.max(max, 1)));
        line.setPrice(unitPrice(product));
        orderItemRepository.save(line);
        refreshOrderTotal(line.getOrder().getOrderId());
        return buildCartResponse(line.getOrder().getOrderId());
    }

    @Transactional
    public Map<String, Object> removeLine(Customer customer, Long orderItemId) {
        OrderItem line = findOwnedCartLine(customer, orderItemId);
        Long orderId = line.getOrder().getOrderId();
        orderItemRepository.delete(line);
        orderItemRepository.flush();
        if (orderItemRepository.findByOrderIdWithProduct(orderId).isEmpty()) {
            orderRepository.deleteById(orderId);
            return emptyCartResponse();
        }
        refreshOrderTotal(orderId);
        return buildCartResponse(orderId);
    }

    /**
     * Places the order. Each product row is locked (in ascending id order, so two checkouts cannot
     * deadlock) and re-read before stock is checked and decremented, so concurrent buyers cannot oversell.
     * Any failure rolls back the whole checkout.
     */
    @Transactional
    public Map<String, Object> checkout(Customer customer, String paymentMethod, String deliveryMode, Long addressId) {
        if (paymentMethod == null || paymentMethod.isBlank()) {
            throw new IllegalArgumentException("Payment method is required");
        }
        boolean pickup = "pickup".equalsIgnoreCase(deliveryMode);
        Order cart = orderRepository.findByCustomerAndStatusIgnoreCase(customer, STATUS_CART)
                .orElseThrow(() -> new IllegalArgumentException("Cart is empty"));
        List<OrderItem> lines = orderItemRepository.findByOrderIdWithProduct(cart.getOrderId()); // ordered by product id
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Cart is empty");
        }

        String shippingAddress = pickup ? "Store pickup" : resolveShippingAddress(customer, addressId);

        BigDecimal itemsTotal = BigDecimal.ZERO;
        List<Product> soldOut = new ArrayList<>();
        for (OrderItem line : lines) {
            Product product = line.getProduct();
            entityManager.refresh(product, LockModeType.PESSIMISTIC_WRITE);

            assertPurchasable(product, customer);
            if (product.getStockQuantity() < line.getQuantity()) {
                throw new IllegalArgumentException(product.getStockQuantity() == 0
                        ? "Sorry, \"" + product.getTitle() + "\" just sold out."
                        : "Only " + product.getStockQuantity() + " left of \"" + product.getTitle()
                                + "\". Please update the quantity in your cart.");
            }
            // Charge the current price, not whatever it was when the item was added.
            line.setPrice(unitPrice(product));
            itemsTotal = itemsTotal.add(line.getPrice().multiply(BigDecimal.valueOf(line.getQuantity())));

            product.setStockQuantity(product.getStockQuantity() - line.getQuantity());
            if (product.getStockQuantity() == 0) {
                product.setStatus(ProductStatus.OUT_OF_STOCK);
                soldOut.add(product);
            }
        }

        BigDecimal grandTotal = itemsTotal.add(pickup ? BigDecimal.ZERO : SHIPPING_COST);

        cart.setStatus(STATUS_PENDING);
        cart.setShippingAddress(shippingAddress);
        cart.setTotalAmount(grandTotal);
        cart.setDate(LocalDate.now());

        // NOTE: no payment provider is integrated; the payment is recorded as completed without charging anyone.
        Payment payment = new Payment();
        payment.setOrder(cart);
        payment.setAmount(grandTotal);
        payment.setPaymentMethod(paymentMethod.trim());
        payment.setStatus("completed");
        payment.setTransactionDetails("SIMULATED - no payment provider integrated");
        payment.setDate(LocalDate.now());
        cart.setPayment(payment);
        orderRepository.save(cart);

        for (Product product : soldOut) {
            if (product.getArtisan() != null) {
                notificationService.sendNotification(product.getArtisan(), "Product Out of Stock",
                        "Your product '" + product.getTitle() + "' has become out of stock.",
                        "OUT_OF_STOCK", "/artisanProducts");
            }
        }
        lines.stream()
                .map(line -> line.getProduct().getArtisan())
                .filter(Objects::nonNull)
                .distinct()
                .forEach(artisan -> notificationService.sendNotification(artisan, "New Order Received",
                        "You have received a new order #" + cart.getOrderId() + " containing your products.",
                        "ORDER", "/artisanOrderDetail?orderId=" + cart.getOrderId()));
        notificationService.sendNotification(customer, "Order Confirmed!",
                "Your order #" + cart.getOrderId() + " has been confirmed and is being processed.",
                "ORDER_CONFIRM", "/orders");

        int orders = customer.getTotalOrders() != null ? customer.getTotalOrders() : 0;
        BigDecimal spent = customer.getTotalSpent() != null ? customer.getTotalSpent() : BigDecimal.ZERO;
        customer.setTotalOrders(orders + 1);
        customer.setTotalSpent(spent.add(grandTotal));
        customerRepository.save(customer);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("orderId", cart.getOrderId());
        result.put("totalAmount", grandTotal);
        result.put("totalOrders", customer.getTotalOrders());
        result.put("totalSpent", customer.getTotalSpent());
        return result;
    }

    /** Price the customer pays per unit: the winning bid for auction lots, the list price otherwise. */
    private BigDecimal unitPrice(Product product) {
        if (product.isAuctionItem()) {
            return auctionRepository.findByProduct_Id(product.getId())
                    .map(Auction::getCurrentHighestBid)
                    .orElse(product.getPrice());
        }
        return product.getPrice();
    }

    private void assertPurchasable(Product product, Customer customer) {
        if (ProductStatus.HIDDEN.equalsIgnoreCase(product.getStatus())) {
            throw new IllegalArgumentException("\"" + product.getTitle() + "\" is no longer available.");
        }
        if (ProductStatus.OUT_OF_STOCK.equalsIgnoreCase(product.getStatus()) || product.getStockQuantity() < 1) {
            throw new IllegalArgumentException("Product is out of stock");
        }
        if (product.isAuctionItem()) {
            Auction auction = auctionRepository.findByProduct_Id(product.getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Auction item cannot be added to the cart at this time"));
            if (!AuctionService.DISPLAY_ENDED.equals(auctionService.resolveDisplayStatus(auction))) {
                throw new IllegalArgumentException(
                        "Auction items can only be added to the cart after the auction ends.");
            }
            if (auction.getHighestBidder() == null
                    || !auction.getHighestBidder().getUserId().equals(customer.getUserId())) {
                throw new IllegalArgumentException("Only the winning bidder can add this auction item to the cart.");
            }
        }
    }

    private String resolveShippingAddress(Customer customer, Long addressId) {
        if (addressId == null) {
            throw new IllegalArgumentException("Shipping address is required");
        }
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new IllegalArgumentException("Address not found"));
        if (customer.getEmail() == null || !customer.getEmail().equalsIgnoreCase(address.getUserEmail())) {
            throw new IllegalArgumentException("Address does not belong to this user");
        }
        return String.format("%s, %s, %s, %s (%s)",
                address.getStreet(), address.getCity(), address.getZip(), address.getCountry(), address.getLabel());
    }

    private OrderItem findOwnedCartLine(Customer customer, Long orderItemId) {
        OrderItem line = orderItemRepository.findByOrderItemId(orderItemId)
                .orElseThrow(() -> new IllegalArgumentException("Cart line not found"));
        Order cart = line.getOrder();
        // Same error for "not yours" and "not a cart line" so ids of other customers' lines are not revealed.
        if (!STATUS_CART.equalsIgnoreCase(cart.getStatus()) || cart.getCustomer() == null
                || !cart.getCustomer().getUserId().equals(customer.getUserId())) {
            throw new IllegalArgumentException("Cart line not found");
        }
        return line;
    }

    private Order createCartOrder(Customer customer) {
        Order o = new Order();
        o.setCustomer(customer);
        o.setStatus(STATUS_CART);
        o.setDate(LocalDate.now());
        o.setTotalAmount(BigDecimal.ZERO);
        o.setShippingAddress("");
        return orderRepository.save(o);
    }

    private void refreshOrderTotal(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.setTotalAmount(subtotal(orderItemRepository.findByOrderIdWithProduct(orderId)));
        orderRepository.save(order);
    }

    private static BigDecimal subtotal(List<OrderItem> lines) {
        return lines.stream()
                .map(li -> li.getPrice().multiply(BigDecimal.valueOf(li.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Map<String, Object> emptyCartResponse() {
        Map<String, Object> m = new HashMap<>();
        m.put("success", true);
        m.put("orderId", null);
        m.put("itemCount", 0);
        m.put("subtotal", BigDecimal.ZERO);
        m.put("items", List.of());
        return m;
    }

    private Map<String, Object> buildCartResponse(Long orderId) {
        List<OrderItem> lines = orderItemRepository.findByOrderIdWithProduct(orderId);
        if (lines.isEmpty()) {
            return emptyCartResponse();
        }
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
            row.put("subtotal", li.getPrice().multiply(BigDecimal.valueOf(li.getQuantity())));
            row.put("maxQuantity", p.isAuctionItem() ? 1 : p.getStockQuantity());
            items.add(row);
        }
        Map<String, Object> m = new HashMap<>();
        m.put("success", true);
        m.put("orderId", orderId);
        m.put("itemCount", lines.stream().mapToInt(OrderItem::getQuantity).sum());
        m.put("subtotal", subtotal(lines));
        m.put("items", items);
        return m;
    }
}
