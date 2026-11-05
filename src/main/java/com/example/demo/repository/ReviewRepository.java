package com.example.demo.repository;

import com.example.demo.model.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByProductIdOrderByDateDesc(Long productId);

    @Query("SELECT r FROM Review r LEFT JOIN FETCH r.customer WHERE r.product.id = :productId AND (r.status IS NULL OR r.status != 'rejected') ORDER BY r.date DESC")
    List<Review> findByProductIdWithCustomerOrderByDateDesc(@Param("productId") Long productId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.product.id = :productId AND (r.status IS NULL OR r.status != 'rejected')")
    Double findAverageRatingByProductId(@Param("productId") Long productId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.product.id = :productId AND (r.status IS NULL OR r.status != 'rejected')")
    long countByProductId(@Param("productId") Long productId);

    @Query("SELECT r FROM Review r WHERE r.product.id = :productId AND (r.status IS NULL OR r.status != 'rejected') ORDER BY r.date DESC")
    List<Review> findActiveByProductIdOrderByDateDesc(@Param("productId") Long productId);

    Optional<Review> findByProductIdAndCustomerEmail(Long productId, String email);

    @Query("SELECT r FROM Review r WHERE r.product.artisan.userId = :artisanId")
    List<Review> findByProductArtisanUserId(@Param("artisanId") Long artisanId);

    @Query("SELECT DISTINCT r FROM Review r LEFT JOIN FETCH r.product LEFT JOIN FETCH r.customer ORDER BY r.date DESC")
    List<Review> findAllWithProductAndCustomer();

    long countByDateBetween(LocalDate from, LocalDate to);

    long countByAutoFlaggedTrue();

    /** Rows of [productId, average rating] over non-rejected reviews of the given products. */
    @Query("SELECT r.product.id, AVG(r.rating) FROM Review r WHERE r.product.id IN :productIds "
            + "AND (r.status IS NULL OR LOWER(r.status) <> 'rejected') GROUP BY r.product.id")
    List<Object[]> averageRatingByProduct(@Param("productIds") java.util.Collection<Long> productIds);

    @EntityGraph(attributePaths = { "product", "customer" })
    List<Review> findByAutoFlaggedTrue();

    /**
     * Admin "Reviews" page, filtered and paged in SQL. Empty string / 0 / false mean "no filter".
     * {@code status} is "active" (not rejected), "rejected" or "".
     */
    @EntityGraph(attributePaths = { "product", "customer" })
    @Query("SELECT r FROM Review r LEFT JOIN r.product p LEFT JOIN r.customer c WHERE "
            + "(:q = '' OR LOWER(p.title) LIKE :q ESCAPE '\\' OR LOWER(c.name) LIKE :q ESCAPE '\\') "
            + "AND (:category = '' OR LOWER(p.category) = LOWER(:category)) "
            + "AND (:rating = 0 OR r.rating = :rating) "
            + "AND (:status = '' "
            + "     OR (:status = 'rejected' AND LOWER(r.status) = 'rejected') "
            + "     OR (:status = 'active' AND (r.status IS NULL OR LOWER(r.status) <> 'rejected'))) "
            + "AND (:flaggedOnly = false OR r.autoFlagged = true)")
    Page<Review> searchForAdmin(@Param("q") String q, @Param("category") String category, @Param("rating") int rating,
            @Param("status") String status, @Param("flaggedOnly") boolean flaggedOnly, Pageable pageable);

    /** Ids of the given products that the customer has already reviewed (one query instead of one per line). */
    @Query("SELECT r.product.id FROM Review r WHERE r.customer.userId = :customerId AND r.product.id IN :productIds")
    List<Long> findReviewedProductIds(@Param("customerId") Long customerId,
            @Param("productIds") java.util.Collection<Long> productIds);

    List<Review> findTop5ByAutoFlaggedTrueAndDateNotNullOrderByDateDesc();
}
