package com.santsg.tourvisio.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "password")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 40)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 40)
    private String lastName;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "phone", unique = true, nullable = true, length = 16)
    private String phone;

    @Column(name = "password", nullable = true, length = 255)
    @JsonIgnore
    private String password;

    @Column(name = "country", nullable = true, length = 100)
    private String country;

    @Column(name = "gender", nullable = true, length = 20)
    private String gender;

    @Column(name = "date_of_birth", nullable = true)
    private LocalDate dateOfBirth;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Authentication provider: LOCAL, GOOGLE */
    @Column(name = "auth_provider", nullable = true, length = 20)
    @Builder.Default
    private String authProvider = "LOCAL";

    @Column(name = "role", length = 20)
    @Builder.Default
    private String role = "user";

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ChatSession> chatSessions = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public String getRole() {
        return role != null ? role : "user";
    }

    public Boolean getIsActive() {
        return isActive != null ? isActive : true;
    }
}

