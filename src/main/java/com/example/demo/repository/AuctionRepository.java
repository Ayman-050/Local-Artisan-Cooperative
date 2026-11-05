package com.example.demo.repository;

import com.example.demo.model.Auction;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AuctionRepository extends JpaRepository<Auction, Long> {

    @Query("SELECT a FROM Auction a LEFT JOIN FETCH a.product")
    List<Auction> findAllWithProduct();

    @Query("SELECT a FROM Auction a LEFT JOIN FETCH a.product p LEFT JOIN FETCH p.artisan WHERE a.id = :id")
    Optional<Auction> findByIdWithProductAndArtisan(@Param("id") Long id);

    List<Auction> findByProductArtisanUserId(Long artisanId);

    Optional<Auction> findByProduct_Id(Long productId);

    List<Auction> findByProduct_IdIn(java.util.Collection<Long> productIds);

    long countByProductArtisanUserId(Long artisanId);

    long countByProductArtisanUserIdAndStatus(Long artisanId, String status);

    List<Auction> findByProductArtisanUserIdAndStatus(Long artisanId, String status);

    @Query("SELECT a FROM Auction a WHERE a.status IS NULL OR UPPER(a.status) NOT IN ('ENDED', 'CANCELLED', 'DELETED')")
    List<Auction> findActiveAndUpcomingAuctions();

    @Query("SELECT a FROM Auction a WHERE UPPER(a.status) IN ('LIVE', 'ACTIVE') AND a.endTime < :now")
    List<Auction> findLiveEndedBefore(@Param("now") LocalDateTime now);

    List<Auction> findTop5ByStatusIgnoreCaseAndEndTimeNotNullOrderByEndTimeDesc(String status);

    @EntityGraph(attributePaths = { "product" })
    List<Auction> findByStatusIgnoreCase(String status);

    List<Auction> findByEndTimeBetween(LocalDateTime from, LocalDateTime to);

    long countByStartTimeBetween(LocalDateTime from, LocalDateTime to);
}
