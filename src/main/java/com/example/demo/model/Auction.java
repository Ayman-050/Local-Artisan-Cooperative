package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "auctions")
public class Auction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "product_id")
    private Product product;

    private double startingBid;
    private double currentHighestBid;
    private String highestBidderName;
    @ManyToOne
    @JoinColumn(name = "highest_bidder_id")
    private Customer highestBidder;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;

    @ManyToOne
    @JoinColumn(name = "admin_id")
    private Admin admin;

    private Boolean flagged = false;
    private String flagReason;
}
