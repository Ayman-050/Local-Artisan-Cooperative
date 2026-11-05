package com.artsyvibe;

import com.artsyvibe.model.Address;
import com.artsyvibe.model.Admin;
import com.artsyvibe.model.Artisan;
import com.artsyvibe.model.Auction;
import com.artsyvibe.model.Customer;
import com.artsyvibe.model.Notification;
import com.artsyvibe.model.Order;
import com.artsyvibe.model.OrderItem;
import com.artsyvibe.model.OrderStatus;
import com.artsyvibe.model.Product;
import com.artsyvibe.model.ProductStatus;
import com.artsyvibe.model.Roles;
import com.artsyvibe.model.User;
import com.artsyvibe.model.UserStatus;
import com.artsyvibe.repository.AddressRepository;
import com.artsyvibe.repository.AuctionRepository;
import com.artsyvibe.repository.NotificationRepository;
import com.artsyvibe.repository.OrderItemRepository;
import com.artsyvibe.repository.OrderRepository;
import com.artsyvibe.repository.ProductRepository;
import com.artsyvibe.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Creates fixtures with unique e-mails/names so test classes can share one database. */
public class TestData {

    public static final String PASSWORD = "Password1";

    private final UserRepository users;
    private final ProductRepository products;
    private final AuctionRepository auctions;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final AddressRepository addresses;
    private final NotificationRepository notifications;
    private final PasswordEncoder encoder;

    public TestData(UserRepository users, ProductRepository products, AuctionRepository auctions,
            OrderRepository orders, OrderItemRepository orderItems, AddressRepository addresses,
            NotificationRepository notifications, PasswordEncoder encoder) {
        this.users = users;
        this.products = products;
        this.auctions = auctions;
        this.orders = orders;
        this.orderItems = orderItems;
        this.addresses = addresses;
        this.notifications = notifications;
        this.encoder = encoder;
    }

    public static String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private <T extends User> T fill(T user, String role, String status) {
        String id = uid();
        user.setName(role.toLowerCase() + " " + id);
        user.setEmail(role.toLowerCase() + "-" + id + "@test.local");
        user.setPassword(encoder.encode(PASSWORD));
        user.setPhone("+97333334444");
        user.setRole(role);
        user.setStatus(status);
        return user;
    }

    public Customer customer() {
        return users.save(fill(new Customer(), Roles.CUSTOMER, UserStatus.ACTIVE));
    }

    public Artisan artisan() {
        return artisan(UserStatus.ACTIVE);
    }

    public Artisan artisan(String status) {
        Artisan a = fill(new Artisan(), Roles.ARTISAN, status);
        a.setShopName("Shop " + uid());
        return users.save(a);
    }

    public Admin admin() {
        return users.save(fill(new Admin(), Roles.ADMIN, UserStatus.ACTIVE));
    }

    public Product product(Artisan artisan, String price, int stock) {
        Product p = new Product();
        p.setTitle("Vase " + uid());
        p.setDescription("Hand made");
        p.setPrice(new BigDecimal(price));
        p.setCategory("Ceramics");
        p.setStockQuantity(stock);
        p.setStatus(ProductStatus.forStock(stock));
        p.setArtisan(artisan);
        p.setArtisanName(artisan.getName());
        p.setAddingDate(LocalDate.now());
        return products.save(p);
    }

    /** An auction lot whose auction is LIVE until {@code end}. */
    public Auction liveAuction(Artisan artisan, String startingBid, LocalDateTime end) {
        Product p = product(artisan, startingBid, 1);
        p.setAuctionItem(true);
        products.save(p);
        Auction a = new Auction();
        a.setProduct(p);
        a.setStartingBid(new BigDecimal(startingBid));
        a.setCurrentHighestBid(new BigDecimal(startingBid));
        a.setStartTime(LocalDateTime.now().minusHours(1));
        a.setEndTime(end);
        a.setStatus("LIVE");
        return auctions.save(a);
    }

    /** An auction that has ended, won by {@code winner} with {@code winningBid}. */
    public Auction endedAuctionWonBy(Artisan artisan, Customer winner, String startingBid, String winningBid) {
        Auction a = liveAuction(artisan, startingBid, LocalDateTime.now().minusMinutes(5));
        a.setStartTime(LocalDateTime.now().minusDays(2));
        a.setCurrentHighestBid(new BigDecimal(winningBid));
        a.setHighestBidder(winner);
        a.setHighestBidderName(winner.getName());
        a.setStatus("ENDED");
        return auctions.save(a);
    }

    public Address address(User owner) {
        Address a = new Address();
        a.setUserEmail(owner.getEmail());
        a.setLabel("Home");
        a.setStreet("1 Road");
        a.setCity("Manama");
        a.setZip("100");
        a.setCountry("Bahrain");
        return addresses.save(a);
    }

    /** A placed order in the given status containing one line of {@code product}. */
    @Transactional
    public OrderItem placedOrder(Customer customer, Product product, String status) {
        Order o = new Order();
        o.setCustomer(customer);
        o.setStatus(status);
        o.setDate(LocalDate.now());
        o.setShippingAddress("1 Road, Manama");
        o.setTotalAmount(product.getPrice());
        orders.save(o);
        OrderItem item = new OrderItem();
        item.setOrder(o);
        item.setProduct(product);
        item.setQuantity(1);
        item.setPrice(product.getPrice());
        item.setFulfilmentStatus(OrderStatus.FULFILMENT_STATUSES.contains(status) ? status : OrderStatus.PENDING);
        return orderItems.save(item);
    }

    /** A pending order with one line from each of the given products (e.g. from different artisans). */
    @Transactional
    public Order multiArtisanOrder(Customer customer, Product... lines) {
        Order o = new Order();
        o.setCustomer(customer);
        o.setStatus(OrderStatus.PENDING);
        o.setDate(LocalDate.now());
        o.setShippingAddress("1 Road, Manama");
        orders.save(o);
        for (Product p : lines) {
            OrderItem item = new OrderItem();
            item.setOrder(o);
            item.setProduct(p);
            item.setQuantity(1);
            item.setPrice(p.getPrice());
            orderItems.save(item);
        }
        return o;
    }

    public OrderItem deliveredOrder(Customer customer, Product product) {
        return placedOrder(customer, product, OrderStatus.DELIVERED);
    }

    public Notification notification(User user) {
        return notifications.save(new Notification(user, "Hello", "Message " + uid(), "GENERAL", "/orders"));
    }
}
