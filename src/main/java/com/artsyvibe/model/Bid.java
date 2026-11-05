package com.artsyvibe.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "bids")
public class Bid {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bidId;
    @Column(precision = 12, scale = 3, nullable = false)
    private BigDecimal bidAmount = BigDecimal.ZERO;
    private LocalDateTime bidTime;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    @JsonIgnoreProperties({ "email", "phone", "address", "role", "status", "joinDate", "lastActive",
            "totalOrders", "totalSpent", "hibernateLazyInitializer", "handler" })
    private Customer customer;

    @ManyToOne
    @JoinColumn(name = "auction_id")
    private Auction auction;
}
