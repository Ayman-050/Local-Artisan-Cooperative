package com.example.demo.repository;

import com.example.demo.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByCategoryIgnoreCase(String category);

    List<Product> findByIsAuctionItem(boolean isAuctionItem);

    List<Product> findByProductCategory_NameIgnoreCase(String categoryName);

    List<Product> findByArtisanUserId(Long artisanId);

    List<Product> findByArtisanUserIdAndIsAuctionItem(Long artisanId, boolean isAuctionItem);

    long countByArtisanUserId(Long artisanId);

    long countByArtisanUserIdAndIsAuctionItem(Long artisanId, boolean isAuctionItem);

    @Query("SELECT DISTINCT p.category FROM Product p WHERE p.category IS NOT NULL AND p.category <> ''")
    List<String> findDistinctCategories();

    /** Products shoppers may see (anything not hidden by an admin). */
    @Query("SELECT p FROM Product p WHERE p.status IS NULL OR LOWER(p.status) <> 'hidden' ORDER BY p.id")
    List<Product> findVisible(Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.isAuctionItem = false AND (p.status IS NULL OR LOWER(p.status) <> 'hidden')")
    List<Product> findVisibleStandardProducts();

    long countByAddingDateBetween(LocalDate from, LocalDate to);

    @Query("SELECT p FROM Product p WHERE LOWER(p.status) IN ('pending', 'pending approval')")
    List<Product> findPendingVerification();

    @Query("SELECT DISTINCT p.artisanName FROM Product p WHERE p.artisanName IS NOT NULL AND p.artisanName <> '' ORDER BY p.artisanName")
    List<String> findDistinctArtisanNames();

    /**
     * Admin "Products" page, filtered and paged in SQL. Empty-string parameters mean "no filter";
     * {@code q} is a LIKE pattern from PageView.likePattern.
     */
    @Query("SELECT p FROM Product p WHERE "
            + "(:q = '' OR LOWER(p.title) LIKE :q ESCAPE '\\' OR LOWER(p.artisanName) LIKE :q ESCAPE '\\') "
            + "AND (:category = '' OR LOWER(p.category) = LOWER(:category)) "
            + "AND (:status = '' OR LOWER(p.status) = LOWER(:status)) "
            + "AND (:artisan = '' OR LOWER(p.artisanName) = LOWER(:artisan))")
    Page<Product> searchForAdmin(@Param("q") String q, @Param("category") String category,
            @Param("status") String status, @Param("artisan") String artisan, Pageable pageable);
}
