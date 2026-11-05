package com.example.demo.service;

import com.example.demo.model.Artisan;
import com.example.demo.model.Auction;
import com.example.demo.model.Product;
import com.example.demo.model.ProductStatus;
import com.example.demo.model.WishlistItem;
import com.example.demo.repository.AuctionRepository;
import com.example.demo.repository.BidRepository;
import com.example.demo.repository.OrderItemRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.ReviewRepository;
import com.example.demo.repository.WishlistItemRepository;
import com.example.demo.web.ImageUrls;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final int MAX_TITLE = 255;
    private static final int MAX_DESCRIPTION = 1000;
    private static final int MAX_STOCK = 100_000;
    private static final BigDecimal MAX_PRICE = new BigDecimal("1000000");

    private final ProductRepository productRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final NotificationService notificationService;
    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final ReviewRepository reviewRepository;
    private final OrderItemRepository orderItemRepository;
    private final AuctionService auctionService;

    /** Editable fields of a product, already parsed from the request. */
    public record ProductInput(String title, String description, BigDecimal price, String category,
            String imageUrl, int stockQuantity, String requestedStatus) {
    }

    @Transactional
    public Product createProduct(Artisan artisan, ProductInput input, boolean isAuctionItem,
            LocalDateTime auctionStart, LocalDateTime auctionEnd) {
        validate(input);
        Product product = new Product();
        product.setArtisan(artisan);
        product.setArtisanName(artisan.getName());
        applyFields(product, input);
        product.setAuctionItem(isAuctionItem);
        if (isAuctionItem) {
            product.setStockQuantity(1); // an auction lot is a single item
        }
        product.setStatus(statusFor(product.getStockQuantity(), input.requestedStatus()));
        product.setAddingDate(LocalDate.now());
        Product saved = productRepository.save(product);

        if (isAuctionItem) {
            createAuction(saved, input.price(), auctionStart, auctionEnd);
        }
        return saved;
    }

    /** Turns an existing standard product of the artisan into an auction lot. */
    @Transactional
    public Auction listForAuction(Product product, BigDecimal startingBid, int durationDays) {
        if (product.isAuctionItem() || auctionRepository.findByProduct_Id(product.getId()).isPresent()) {
            throw new IllegalArgumentException("This product is already listed for auction.");
        }
        if (durationDays < 1 || durationDays > 30) {
            throw new IllegalArgumentException("Auction duration must be between 1 and 30 days.");
        }
        assertValidPrice(startingBid);
        product.setAuctionItem(true);
        product.setPrice(startingBid);
        LocalDateTime now = LocalDateTime.now();
        return createAuction(product, startingBid, now, now.plusDays(durationDays));
    }

    private Auction createAuction(Product product, BigDecimal startingBid, LocalDateTime start, LocalDateTime end) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime effectiveStart = start != null ? start : now;
        LocalDateTime effectiveEnd = end != null ? end : effectiveStart.plusDays(7);
        if (!effectiveEnd.isAfter(effectiveStart)) {
            throw new IllegalArgumentException("Auction end time must be after the start time.");
        }
        if (!effectiveEnd.isAfter(now)) {
            throw new IllegalArgumentException("Auction end time must be in the future.");
        }
        Auction auction = new Auction();
        auction.setProduct(product);
        auction.setStartingBid(startingBid);
        auction.setCurrentHighestBid(startingBid);
        auction.setStartTime(effectiveStart);
        auction.setEndTime(effectiveEnd);
        boolean live = !effectiveStart.isAfter(now);
        auction.setStatus(live ? AuctionService.DISPLAY_LIVE : AuctionService.DISPLAY_UPCOMING);
        Auction saved = auctionRepository.save(auction);
        if (live) {
            auctionService.triggerAuctionStartNotification(saved);
        }
        return saved;
    }

    @Transactional
    public Product updateProduct(Product product, ProductInput input) {
        validate(input);
        BigDecimal oldPrice = product.getPrice();
        boolean wasAvailable = isAvailable(product);

        applyFields(product, input);
        if (product.isAuctionItem()) {
            product.setStockQuantity(Math.min(product.getStockQuantity(), 1));
        }
        // A product an admin hid stays hidden: artisans cannot re-publish it by editing.
        if (!ProductStatus.HIDDEN.equalsIgnoreCase(product.getStatus())) {
            product.setStatus(statusFor(product.getStockQuantity(), input.requestedStatus()));
        }

        boolean available = isAvailable(product);
        if (oldPrice != null && product.getPrice().compareTo(oldPrice) < 0) {
            notifyWishlistUsers(product, "Price Drop!", "An item in your wishlist, " + product.getTitle()
                    + ", is now cheaper! Only " + product.getPrice().stripTrailingZeros().toPlainString() + " BD");
        }
        if (!wasAvailable && available) {
            notifyWishlistUsers(product, "Back in Stock!", "Good news! " + product.getTitle() + " is back in stock.");
        }
        if (wasAvailable && !available && product.getArtisan() != null) {
            notificationService.sendNotification(product.getArtisan(), "Product Out of Stock",
                    "Your product '" + product.getTitle() + "' has become out of stock.",
                    "OUT_OF_STOCK", "/artisanProducts");
        }
        return productRepository.save(product);
    }

    /**
     * Removes a product. Products that appear in orders or have received bids are part of the sales history,
     * so they are hidden instead of deleted (deleting them used to fail on foreign keys or blank out the
     * order history).
     *
     * @return true when the product was physically deleted, false when it was hidden
     */
    @Transactional
    public boolean deleteOrHide(Product product) {
        Long id = product.getId();
        Optional<Auction> auction = auctionRepository.findByProduct_Id(id);
        boolean hasHistory = !orderItemRepository.findByProduct_Id(id).isEmpty()
                || auction.map(a -> bidRepository.countByAuctionId(a.getId()) > 0).orElse(false);

        List<WishlistItem> wishlistItems = wishlistItemRepository.findByProduct(product);
        wishlistItemRepository.deleteAll(wishlistItems);

        if (hasHistory) {
            product.setStatus(ProductStatus.HIDDEN);
            auction.ifPresent(a -> {
                if (!AuctionService.DISPLAY_ENDED.equals(auctionService.resolveDisplayStatus(a))) {
                    a.setStatus(AuctionService.STATUS_CANCELLED);
                }
            });
            productRepository.save(product);
            return false;
        }
        auction.ifPresent(auctionRepository::delete);
        reviewRepository.deleteAll(reviewRepository.findByProductIdOrderByDateDesc(id));
        productRepository.delete(product);
        return true;
    }

    private static void applyFields(Product product, ProductInput input) {
        product.setTitle(input.title().trim());
        product.setDescription(input.description() != null ? input.description().trim() : null);
        product.setPrice(input.price());
        product.setCategory(input.category() != null ? input.category().trim() : null);
        product.setImageUrl(ImageUrls.validate(input.imageUrl(), "Product image"));
        product.setStockQuantity(input.stockQuantity());
    }

    /** Artisans may only choose between "active" and "out of stock"; stock 0 always means out of stock. */
    private static String statusFor(int stock, String requested) {
        if (stock <= 0 || ProductStatus.OUT_OF_STOCK.equalsIgnoreCase(requested)) {
            return ProductStatus.OUT_OF_STOCK;
        }
        return ProductStatus.ACTIVE;
    }

    private static boolean isAvailable(Product p) {
        return p.getStockQuantity() > 0 && ProductStatus.ACTIVE.equalsIgnoreCase(p.getStatus());
    }

    private static void validate(ProductInput input) {
        if (input.title() == null || input.title().isBlank()) {
            throw new IllegalArgumentException("Title is required.");
        }
        if (input.title().length() > MAX_TITLE) {
            throw new IllegalArgumentException("Title is too long.");
        }
        if (input.description() != null && input.description().length() > MAX_DESCRIPTION) {
            throw new IllegalArgumentException("Description is too long (" + MAX_DESCRIPTION + " characters max).");
        }
        if (input.category() != null && input.category().length() > 255) {
            throw new IllegalArgumentException("Category is too long.");
        }
        assertValidPrice(input.price());
        if (input.stockQuantity() < 0 || input.stockQuantity() > MAX_STOCK) {
            throw new IllegalArgumentException("Stock quantity must be between 0 and " + MAX_STOCK + ".");
        }
    }

    private static void assertValidPrice(BigDecimal price) {
        if (price == null || price.signum() <= 0 || price.compareTo(MAX_PRICE) > 0) {
            throw new IllegalArgumentException("Price must be greater than 0.");
        }
    }

    private void notifyWishlistUsers(Product product, String title, String message) {
        for (WishlistItem item : wishlistItemRepository.findByProduct(product)) {
            if (item.getWishlist() != null && item.getWishlist().getCustomer() != null) {
                notificationService.sendNotification(item.getWishlist().getCustomer(), title, message,
                        "WISHLIST_UPDATE", "/ProductDetailsStandard?id=" + product.getId());
            }
        }
    }
}
