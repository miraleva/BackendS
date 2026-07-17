package com.santsg.tourvisio.repository;

import com.santsg.tourvisio.entity.PasswordResetToken;
import com.santsg.tourvisio.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    // E-posta ile gelen token değerine göre veritabanında arama yapmak için
    Optional<PasswordResetToken> findByToken(String token);

    // Eğer kullanıcının eski aktif token'ları varsa yenisini oluşturmadan önce temizlemek için
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.user = :user")
    void deleteByUser(User user);
}