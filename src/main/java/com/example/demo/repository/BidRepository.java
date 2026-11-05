package com.example.demo.repository;

import com.example.demo.model.Bid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BidRepository extends JpaRepository<Bid, Long> {
    List<Bid> findByAuctionIdOrderByBidTimeDesc(Long auctionId);

    @Query("SELECT b FROM Bid b LEFT JOIN FETCH b.customer WHERE b.auction.id = :auctionId ORDER BY b.bidTime DESC")
    List<Bid> findByAuctionIdWithCustomerOrderByBidTimeDesc(@Param("auctionId") Long auctionId);

    long countByAuctionId(Long auctionId);

    @Query("SELECT COUNT(DISTINCT b.customer.userId) FROM Bid b WHERE b.auction.id = :auctionId")
    long countDistinctBiddersByAuctionId(@Param("auctionId") Long auctionId);
}
