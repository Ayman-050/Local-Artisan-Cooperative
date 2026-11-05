package com.artsyvibe.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.math.BigDecimal;
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

    @Column(precision = 12, scale = 3, nullable = false)
    private BigDecimal startingBid = BigDecimal.ZERO;
    @Column(precision = 12, scale = 3, nullable = false)
    private BigDecimal currentHighestBid = BigDecimal.ZERO;
    private String highestBidderName;
    @ManyToOne
    @JoinColumn(name = "highest_bidder_id")
    @JsonIgnoreProperties({ "email", "phone", "address", "role", "status", "joinDate", "lastActive",
            "totalOrders", "totalSpent", "hibernateLazyInitializer", "handler" })
    private Customer highestBidder;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;

    @ManyToOne
    @JoinColumn(name = "admin_id")
    @JsonIgnore
    private Admin admin;

    private Boolean flagged = false;
    private String flagReason;
}
