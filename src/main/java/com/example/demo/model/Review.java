package com.example.demo.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "reviews")
public class Review {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long reviewId;

    private int rating;
    @Column(columnDefinition = "TEXT")
    private String comment;
    private LocalDate date;

    private String status;
    private Boolean autoFlagged;
    private String rejectionReason;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    @JsonIgnoreProperties({ "email", "phone", "address", "role", "status", "joinDate", "lastActive",
            "totalOrders", "totalSpent", "hibernateLazyInitializer", "handler" })
    private Customer customer;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;
}
