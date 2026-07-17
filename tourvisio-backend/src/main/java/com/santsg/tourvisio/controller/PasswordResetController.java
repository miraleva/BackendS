package com.santsg.tourvisio.controller;

import com.santsg.tourvisio.dto.auth.ForgotPasswordRequest;
import com.santsg.tourvisio.dto.auth.ResetPasswordRequest;
import com.santsg.tourvisio.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Frontend portunuza göre sınırlandırabilirsiniz (örn: http://localhost:3000)
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    // 1. Şifremi Unuttum - E-posta gönderme isteği
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        try {
            passwordResetService.initiatePasswordReset(request.getEmail());
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Password reset link has been sent to your email."
            ));
        } catch (RuntimeException e) {
            // Güvenlik gereği "kullanıcı bulunamadı" hatasını dışarıya çok detaylı vermemek de bir tercihtir.
            // Ancak geliştirme aşamasında hatayı net görmek için doğrudan mesajı döndürüyoruz.
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        }
    }

    // 2. Yeni Şifre Kaydetme isteği
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        try {
            passwordResetService.resetPassword(request.getToken(), request.getPassword());
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Your password has been successfully reset."
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        }
    }
}