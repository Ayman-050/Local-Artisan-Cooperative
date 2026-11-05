package com.artsyvibe.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    private String title;

    @Column(length = 4000)
    private String message;
    private String type; // e.g., AUCTION_START, BID_OUTBID, ORDER_SHIPPED
    private LocalDateTime timestamp;
    private boolean isRead = false;
    private String link; // Optional link to redirect user (e.g., to order or auction page)

    public Notification() {
        this.timestamp = LocalDateTime.now();
    }

    public Notification(User user, String title, String message, String type, String link) {
        this.user = user;
        this.title = title;
        this.message = message;
        this.type = type;
        this.link = link;
        this.timestamp = LocalDateTime.now();
        this.isRead = false;
    }
}
