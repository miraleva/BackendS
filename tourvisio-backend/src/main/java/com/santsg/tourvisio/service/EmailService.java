package com.santsg.tourvisio.service;

import com.santsg.tourvisio.entity.Passenger;
import com.santsg.tourvisio.entity.Reservation;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("sannydestek@gmail.com");
            message.setTo(toEmail);
            message.setSubject("Sanny - Password Reset Request");
            message.setText("Hello,\n\n"
                    + "We received a request to reset your password. "
                    + "Please click the link below to set a new password:\n\n"
                    + resetLink + "\n\n"
                    + "This link will expire in 15 minutes.\n\n"
                    + "If you did not request this, please ignore this email.\n\n"
                    + "Best regards,\nSanny Team");

            mailSender.send(message);
        } catch (Exception e) {
            log.error("[EmailService] Failed to send password reset email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendReservationConfirmationEmail(Reservation reservation, String recipientEmail, String customerName) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            log.warn("[EmailService] Cannot send reservation email: recipient email is missing");
            return;
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom("sannydestek@gmail.com");
            helper.setTo(recipientEmail);
            helper.setSubject("Sanny - Rezervasyon ve Bilet Onayı [" + reservation.getReservationNumber() + "]");

            String html = buildReservationHtmlTemplate(reservation, customerName);
            helper.setText(html, true);

            mailSender.send(mimeMessage);
            log.info("[EmailService] Asynchronous confirmation email sent successfully to {} for PNR {}", recipientEmail, reservation.getReservationNumber());
        } catch (Exception e) {
            log.error("[EmailService] Failed to send confirmation email to {}: {}", recipientEmail, e.getMessage(), e);
        }
    }

    private String buildReservationHtmlTemplate(Reservation reservation, String customerName) {
        String name = (customerName != null && !customerName.isBlank()) ? customerName : "Değerli Müşterimiz";
        String typeLabel = "FLIGHT".equalsIgnoreCase(reservation.getType()) ? "✈️ Uçak Bileti" : "🏨 Otel Rezervasyonu";
        String pnr = reservation.getReservationNumber() != null ? reservation.getReservationNumber() : "N/A";
        String itemName = reservation.getItemName() != null ? reservation.getItemName() : "-";
        String destination = reservation.getDestination() != null ? reservation.getDestination() : "-";
        String startDate = reservation.getStartDate() != null ? reservation.getStartDate().toString() : "-";
        String endDate = reservation.getEndDate() != null ? reservation.getEndDate().toString() : "-";
        String totalPrice = (reservation.getTotalPrice() != null ? String.format("%.2f", reservation.getTotalPrice()) : "0.00")
                + " " + (reservation.getCurrency() != null ? reservation.getCurrency() : "TRY");

        StringBuilder passengerRows = new StringBuilder();
        List<Passenger> passengers = reservation.getPassengers();
        if (passengers != null && !passengers.isEmpty()) {
            for (Passenger p : passengers) {
                passengerRows.append("<tr>")
                        .append("<td style='padding: 8px 12px; border-bottom: 1px solid #e2e8f0;'>").append(p.getFirstName() != null ? p.getFirstName() : "").append(" ").append(p.getLastName() != null ? p.getLastName() : "").append("</td>")
                        .append("<td style='padding: 8px 12px; border-bottom: 1px solid #e2e8f0;'>").append(p.getEmail() != null ? p.getEmail() : "-").append("</td>")
                        .append("<td style='padding: 8px 12px; border-bottom: 1px solid #e2e8f0;'>").append(p.getPhoneNumber() != null ? p.getPhoneNumber() : "-").append("</td>")
                        .append("</tr>");
            }
        } else {
            passengerRows.append("<tr><td colspan='3' style='padding: 8px 12px; color: #64748b;'>Yolcu detayı bulunmamaktadır.</td></tr>");
        }

        return "<!DOCTYPE html>"
                + "<html>"
                + "<head><meta charset='UTF-8'></head>"
                + "<body style='font-family: Arial, Helvetica, sans-serif; background-color: #f8fafc; margin: 0; padding: 20px; color: #1e293b;'>"
                + "  <div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 16px; overflow: hidden; border: 1px solid #e2e8f0; box-shadow: 0 4px 12px rgba(0,0,0,0.05);'>"
                + "    <div style='background-color: #2563eb; padding: 24px; text-align: center; color: #ffffff;'>"
                + "      <h1 style='margin: 0; font-size: 24px; font-weight: bold;'>SANNY TRAVEL</h1>"
                + "      <p style='margin: 4px 0 0 0; font-size: 14px; opacity: 0.9;'>Rezervasyon & Bilet Onayı</p>"
                + "    </div>"
                + "    <div style='padding: 32px;'>"
                + "      <h2 style='font-size: 18px; color: #0f172a; margin-top: 0;'>Merhaba " + name + ",</h2>"
                + "      <p style='font-size: 14px; line-height: 1.6; color: #475569;'>Rezervasyon işleminiz başarıyla tamamlanmıştır. Detaylar aşağıda özetlenmiştir:</p>"
                + "      <div style='background-color: #eff6ff; border: 1px solid #bfdbfe; border-radius: 12px; padding: 16px; margin: 20px 0; text-align: center;'>"
                + "        <span style='font-size: 12px; color: #1e40af; text-transform: uppercase; font-weight: bold; letter-spacing: 1px;'>Rezervasyon / PNR Kodu</span>"
                + "        <div style='font-size: 26px; font-weight: 800; color: #1d4ed8; margin-top: 4px;'>" + pnr + "</div>"
                + "      </div>"
                + "      <h3 style='font-size: 15px; color: #1e293b; border-bottom: 2px solid #f1f5f9; padding-bottom: 8px; margin-top: 24px;'>Özet Detaylar</h3>"
                + "      <table style='width: 100%; font-size: 14px; border-collapse: collapse; margin-bottom: 20px;'>"
                + "        <tr><td style='padding: 8px 0; color: #64748b; width: 40%;'>Hizmet Türü:</td><td style='padding: 8px 0; font-weight: bold;'>" + typeLabel + "</td></tr>"
                + "        <tr><td style='padding: 8px 0; color: #64748b;'>Hizmet Adı:</td><td style='padding: 8px 0; font-weight: bold;'>" + itemName + "</td></tr>"
                + "        <tr><td style='padding: 8px 0; color: #64748b;'>Destinasyon:</td><td style='padding: 8px 0; font-weight: bold;'>" + destination + "</td></tr>"
                + "        <tr><td style='padding: 8px 0; color: #64748b;'>Başlangıç Tarihi:</td><td style='padding: 8px 0; font-weight: bold;'>" + startDate + "</td></tr>"
                + "        <tr><td style='padding: 8px 0; color: #64748b;'>Bitiş Tarihi:</td><td style='padding: 8px 0; font-weight: bold;'>" + endDate + "</td></tr>"
                + "        <tr><td style='padding: 8px 0; color: #64748b;'>Toplam Tutar:</td><td style='padding: 8px 0; font-weight: 800; color: #2563eb; font-size: 16px;'>" + totalPrice + "</td></tr>"
                + "      </table>"
                + "      <h3 style='font-size: 15px; color: #1e293b; border-bottom: 2px solid #f1f5f9; padding-bottom: 8px; margin-top: 24px;'>Konuk / Yolcu Bilgileri</h3>"
                + "      <table style='width: 100%; font-size: 13px; border-collapse: collapse; text-align: left;'>"
                + "        <thead><tr style='background-color: #f8fafc; color: #475569;'><th style='padding: 8px 12px;'>Ad Soyad</th><th style='padding: 8px 12px;'>E-posta</th><th style='padding: 8px 12px;'>Telefon</th></tr></thead>"
                + "        <tbody>" + passengerRows.toString() + "</tbody>"
                + "      </table>"
                + "    </div>"
                + "    <div style='background-color: #f1f5f9; padding: 16px; text-align: center; font-size: 12px; color: #64748b; border-top: 1px solid #e2e8f0;'>"
                + "      <p style='margin: 0;'>Bu e-posta Sanny Otomatik Rezervasyon Sistemi tarafından gönderilmiştir.</p>"
                + "      <p style='margin: 4px 0 0 0;'>Sorularınız için <a href='mailto:sannydestek@gmail.com' style='color: #2563eb;'>sannydestek@gmail.com</a> adresinden bize ulaşabilirsiniz.</p>"
                + "    </div>"
                + "  </div>"
                + "</body>"
                + "</html>";
    }
}