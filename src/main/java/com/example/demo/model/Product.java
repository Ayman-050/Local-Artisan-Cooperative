package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Data;
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
    private double price;
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
    private Artisan artisan;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private ProductCategory productCategory;
}
