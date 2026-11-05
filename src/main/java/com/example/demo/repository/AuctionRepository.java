package com.example.demo.repository;

import com.example.demo.model.Auction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AuctionRepository extends JpaRepository<Auction, Long> {

    @Query("SELECT a FROM Auction a LEFT JOIN FETCH a.product")
    List<Auction> findAllWithProduct();

    @Query("SELECT a FROM Auction a LEFT JOIN FETCH a.product p LEFT JOIN FETCH p.artisan WHERE a.id = :id")
    Optional<Auction> findByIdWithProductAndArtisan(@Param("id") Long id);

    List<Auction> findByProductArtisanUserId(Long artisanId);

    Optional<Auction> findByProduct_Id(Long productId);

    long countByProductArtisanUserId(Long artisanId);

    long countByProductArtisanUserIdAndStatus(Long artisanId, String status);

    List<Auction> findByProductArtisanUserIdAndStatus(Long artisanId, String status);

    @org.springframework.data.jpa.repository.Query("SELECT a FROM Auction a WHERE a.status IS NULL OR UPPER(a.status) <> 'ENDED'")
    List<Auction> findActiveAndUpcomingAuctions();
}