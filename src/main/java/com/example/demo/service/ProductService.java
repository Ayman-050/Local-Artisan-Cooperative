package com.example.demo.service;

import com.example.demo.model.Product;
import com.example.demo.model.WishlistItem;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.WishlistItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private WishlistItemRepository wishlistItemRepository;

    @Autowired
    private NotificationService notificationService;

    @Transactional
    public Product updateProduct(Long productId, Product updatedDetails) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        double oldPrice = product.getPrice();
        int oldStock = product.getStockQuantity();
        String oldStatus = product.getStatus();
        boolean wasAvailable = (oldStock > 0 && !"out of stock".equalsIgnoreCase(oldStatus));

        product.setTitle(updatedDetails.getTitle());
        product.setDescription(updatedDetails.getDescription());
        product.setPrice(updatedDetails.getPrice());
        product.setStockQuantity(updatedDetails.getStockQuantity());
        if (updatedDetails.getStockQuantity() <= 0) {
            product.setStatus("out of stock");
        } else if (updatedDetails.getStatus() != null) {
            product.setStatus(updatedDetails.getStatus());
        } else {
            product.setStatus("active");
        }
        product.setCategory(updatedDetails.getCategory());
        product.setImageUrl(updatedDetails.getImageUrl());

        productRepository.save(product);

        boolean isAvailable = (product.getStockQuantity() > 0 && !"out of stock".equalsIgnoreCase(product.getStatus()));

        // Check for discount or restock to notify wishlist users
        if (product.getPrice() < oldPrice) {
            notifyWishlistUsers(product, "Price Drop!", "An item in your wishlist, " + product.getTitle() + ", is now cheaper! Only " + product.getPrice() + " BD");
        }
        
        if (!wasAvailable && isAvailable) {
            notifyWishlistUsers(product, "Back in Stock!", "Good news! " + product.getTitle() + " is back in stock.");
        }

        // Notify artisan if product became out of stock
        boolean becameOutOfStock = (wasAvailable && !isAvailable);
        if (becameOutOfStock && product.getArtisan() != null) {
            notificationService.sendNotification(
                product.getArtisan(),
                "Product Out of Stock",
                "Your product '" + product.getTitle() + "' has become out of stock.",
                "OUT_OF_STOCK",
                "/artisan/products"
            );
        }

        return product;
    }

    private void notifyWishlistUsers(Product product, String title, String message) {
        List<WishlistItem> items = wishlistItemRepository.findByProduct(product);
        for (WishlistItem item : items) {
            if (item.getWishlist() != null && item.getWishlist().getCustomer() != null) {
                notificationService.sendNotification(
                    item.getWishlist().getCustomer(),
                    title,
                    message,
                    "WISHLIST_UPDATE",
                    "/ProductDetailsStandard?id=" + product.getId()
                );
            }
        }
    }
}
