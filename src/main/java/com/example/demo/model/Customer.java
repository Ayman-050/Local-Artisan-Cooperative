package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "customers")
public class Customer extends User {
    private String address;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private Integer totalOrders = 0;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal totalSpent = BigDecimal.ZERO;

    @PostLoad
    private void normalizeStats() {
        if (totalOrders == null) {
            totalOrders = 0;
        }
        if (totalSpent == null) {
            totalSpent = BigDecimal.ZERO;
        }
    }
}
