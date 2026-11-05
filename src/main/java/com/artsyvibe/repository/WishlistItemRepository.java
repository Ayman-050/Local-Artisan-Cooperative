package com.artsyvibe.repository;

import com.artsyvibe.model.Wishlist;
import com.artsyvibe.model.WishlistItem;
import com.artsyvibe.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Repository
public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {
    Optional<WishlistItem> findByWishlistAndProduct(Wishlist wishlist, Product product);
    
    @Transactional
    void deleteByWishlistAndProduct(Wishlist wishlist, Product product);

    java.util.List<WishlistItem> findByProduct(Product product);
}
