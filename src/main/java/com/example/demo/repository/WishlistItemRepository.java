package com.example.demo.repository;

import com.example.demo.model.Wishlist;
import com.example.demo.model.WishlistItem;
import com.example.demo.model.Product;
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
