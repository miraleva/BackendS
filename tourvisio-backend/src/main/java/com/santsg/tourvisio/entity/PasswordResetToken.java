package com.santsg.tourvisio.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "password_reset_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    // Her token bir kullanıcıya ait olmalıdır (One-to-One ilişki)
    @OneToOne(targetEntity = User.class, fetch = FetchType.EAGER)
    @JoinColumn(nullable = false, name = "user_id")
    private User user;

    @Column(nullable = false)
    private LocalDateTime expiryDate;

    // Token oluşturulurken varsayılan olarak geçerlilik süresi belirleyen yardımcı constructor
    public PasswordResetToken(String token, User user, int expiryTimeInMinutes) {
        this.token = token;
        this.user = user;
        this.expiryDate = LocalDateTime.now().plusMinutes(expiryTimeInMinutes);
    }

    // Token süresinin dolup dolmadığını kontrol eden yardımcı metot
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiryDate);
    }
}