package com.example.demo.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    @Column(length = 1000)
    private String description;
    @Column(precision = 12, scale = 3, nullable = false)
    private BigDecimal price = BigDecimal.ZERO;
    private String category;
    @Column(columnDefinition = "TEXT")
    private String imageUrl;
    private int stockQuantity;
    private String status = "active";
    private String artisanName;
    private boolean isAuctionItem;
    private LocalDate addingDate;

    @ManyToOne
    @JoinColumn(name = "artisan_id")
    @JsonIgnoreProperties({ "email", "phone", "role", "status", "joinDate", "lastActive", "shopBanner",
            "hibernateLazyInitializer", "handler" })
    private Artisan artisan;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private ProductCategory productCategory;
}
