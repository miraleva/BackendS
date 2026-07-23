package com.santsg.tourvisio.service;

import com.santsg.tourvisio.entity.PasswordResetToken;
import com.santsg.tourvisio.entity.User;
import com.santsg.tourvisio.repository.PasswordResetTokenRepository;
import com.santsg.tourvisio.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    private static final int EXPIRATION_MINUTES = 15;

    @Transactional
    public void initiatePasswordReset(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        // 1. Varsa kullanıcının eski token kayıtlarını silmesini söylüyoruz
        tokenRepository.deleteByUser(user);

        // 2. KÖKTEN ÇÖZÜM: Silme işlemini veritabanına HEMEN commit ediyoruz.
        // Böylece JPA, yeni INSERT işlemini yapmadan önce DELETE işleminin bittiğinden emin olur
        // ve veritabanındaki "unique user_id" kısıtlaması patlamaz.
        tokenRepository.flush();

        // 3. Güvenle yeni token kaydını oluşturup kaydediyoruz
        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = new PasswordResetToken(token, user, EXPIRATION_MINUTES);

        tokenRepository.save(resetToken);

        String resetLink = "http://localhost:3000/reset-password?token=" + token;

        emailService.sendPasswordResetEmail(user.getEmail(), resetLink);
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid password reset token."));

        if (resetToken.isExpired()) {
            tokenRepository.delete(resetToken);
            throw new RuntimeException("Password reset token has expired.");
        }

        User user = resetToken.getUser();
        
        // BCrypt ile PasswordEncoder kullanarak şifreyi hash'leyip kaydediyoruz
        String hashedPassword = passwordEncoder.encode(newPassword);
        user.setPassword(hashedPassword);
        userRepository.save(user);

        tokenRepository.delete(resetToken);
    }
}