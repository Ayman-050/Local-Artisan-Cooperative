package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "artisans")
public class Artisan extends User {
    private String shopName;
    private String biography;
    private String phone;
    @Column(columnDefinition = "TEXT")
    private String shopBanner = "https://images.unsplash.com/photo-1459411552884-841db9b3cc2a?w=800&h=300&fit=crop";
}
