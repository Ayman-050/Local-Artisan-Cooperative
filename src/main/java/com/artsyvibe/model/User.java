package com.artsyvibe.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.JOINED)
public class User {

    public static final String DEFAULT_PROFILE_PICTURE = "https://cdn-icons-png.flaticon.com/512/149/149071.png";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    private String name;
    private String phone;
    private String email;

    /** BCrypt hash. Never serialized: entities are returned as JSON from several endpoints. */
    @JsonIgnore
    @ToString.Exclude
    private String password;

    /** One of {@link Roles}. */
    private String role;

    @Column(columnDefinition = "TEXT")
    private String profilePicture = DEFAULT_PROFILE_PICTURE;

    /** One of {@link UserStatus}. */
    private String status = UserStatus.ACTIVE;
    private LocalDateTime joinDate;
    private LocalDateTime lastActive;

    @PrePersist
    protected void onCreate() {
        if (joinDate == null) {
            joinDate = LocalDateTime.now();
        }
    }
}
