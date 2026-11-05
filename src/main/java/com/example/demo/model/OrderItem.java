package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "order_items")
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long orderItemId;

    private int quantity;
    private double price;

    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    private boolean refundRequested;
    private String refundStatus; // PENDING, APPROVED, DECLINED, ESCALATED
    
    @Column(length = 4000)
    private String refundReason;
    
    @Column(columnDefinition = "TEXT")
    private String refundImages;

    @Column(length = 4000)
    private String artisanRefusalReason;

    @Column(name = "admin_refund_status")
    private String adminRefundStatus = "PENDING"; // PENDING, APPROVED, REJECTED

    @Column(length = 4000)
    private String adminNote;

    /** When the admin approved or rejected the refund (used for monthly/weekly stats). */
    private LocalDateTime adminRefundProcessedAt;
}
