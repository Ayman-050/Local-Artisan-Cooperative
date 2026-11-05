package com.example.demo.repository;

import com.example.demo.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByCategoryIgnoreCase(String category);
    List<Product> findByIsAuctionItem(boolean isAuctionItem);
    List<Product> findByProductCategory_NameIgnoreCase(String categoryName);
    List<Product> findByArtisanUserId(Long artisanId);
    List<Product> findByArtisanUserIdAndIsAuctionItem(Long artisanId, boolean isAuctionItem);
    long countByArtisanUserId(Long artisanId);
    long countByArtisanUserIdAndIsAuctionItem(Long artisanId, boolean isAuctionItem);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT p.category FROM Product p WHERE p.category IS NOT NULL AND p.category <> ''")
    List<String> findDistinctCategories();
}
