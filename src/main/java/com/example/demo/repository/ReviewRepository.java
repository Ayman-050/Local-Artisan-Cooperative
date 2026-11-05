package com.example.demo.repository;

import com.example.demo.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
