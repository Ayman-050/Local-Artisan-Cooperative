package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "customers")
public class Customer extends User {
    private String address;
// duplicate phone removed

    @Column(nullable = false, columnDefinition = "integer default 0")
    private Integer totalOrders = 0;

    @Column(nullable = false, columnDefinition = "double precision default 0")
    private Double totalSpent = 0.0;

    @PostLoad
    private void normalizeStats() {
        if (totalOrders == null) {
            totalOrders = 0;
        }
        if (totalSpent == null) {
            totalSpent = 0.0;
        }
    }
}
