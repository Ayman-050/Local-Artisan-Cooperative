package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "addresses")
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userEmail;

    private String label;

    @Column(length = 500)
    private String street;

    private String city;
    private String zip;
    private String country;
    private boolean isDefault;
}
