package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "order_items")
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long orderItemId;

    private int quantity;
    @Column(precision = 12, scale = 3, nullable = false)
    private BigDecimal price = BigDecimal.ZERO;

    @ManyToOne
    @JoinColumn(name = "order_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    private Order order;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    /**
     * Fulfilment of this line by the artisan who sells it (pending/processing/shipped/delivered).
     * The order's own status is derived from its lines, see OrderStatus.aggregate.
     */
    @Column(name = "fulfilment_status")
    private String fulfilmentStatus = OrderStatus.PENDING;

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
