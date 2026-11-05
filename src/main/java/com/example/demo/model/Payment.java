package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "payments")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentId;

    private String transactionDetails;
    private LocalDate date;
    private String status;
    private double amount;
    private String paymentMethod;

    @OneToOne
    @JoinColumn(name = "order_id")
    private Order order;
}
