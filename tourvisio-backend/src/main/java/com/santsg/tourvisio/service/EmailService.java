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
            log.error("[EmailService] Failed to send password reset email: {}", e.getMessage());
        }
    }

    @Async
    public void sendOtpEmail(String toEmail, String otpCode) {
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("[EmailService] Cannot send OTP email: recipient email is missing");
            return;
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom("sannydestek@gmail.com");
            helper.setTo(toEmail);
            helper.setSubject("SANNY - E-posta Doğrulama Kodu [" + otpCode + "]");

            String html = "<!DOCTYPE html>"
                    + "<html>"
                    + "<head><meta charset='UTF-8'></head>"
                    + "<body style='font-family: Arial, Helvetica, sans-serif; background-color: #f8fafc; margin: 0; padding: 20px; color: #1e293b;'>"
                    + "  <div style='max-width: 500px; margin: 0 auto; background-color: #ffffff; border-radius: 16px; overflow: hidden; border: 1px solid #e2e8f0; box-shadow: 0 4px 12px rgba(0,0,0,0.05);'>"
                    + "    <div style='background-color: #2563eb; padding: 24px; text-align: center; color: #ffffff;'>"
                    + "      <h1 style='margin: 0; font-size: 24px; font-weight: bold;'>SANNY TRAVEL</h1>"
                    + "      <p style='margin: 4px 0 0 0; font-size: 13px; opacity: 0.9;'>E-posta Doğrulama Servisi</p>"
                    + "    </div>"
                    + "    <div style='padding: 32px; text-align: center;'>"
                    + "      <h2 style='font-size: 18px; color: #0f172a; margin-top: 0;'>Güvenlik Doğrulama Kodu</h2>"
                    + "      <p style='font-size: 14px; line-height: 1.6; color: #475569;'>SANNY hesabınız için doğrulama talebinde bulundunuz. Aşağıdaki 6 haneli kodu doğrulama ekranına giriniz:</p>"
                    + "      <div style='background-color: #eff6ff; border: 2px stroke #2563eb; border-radius: 12px; padding: 16px; margin: 24px 0;'>"
                    + "        <span style='font-size: 32px; font-weight: 800; color: #1d4ed8; letter-spacing: 6px;'>" + otpCode + "</span>"
                    + "      </div>"
                    + "      <p style='font-size: 12px; color: #64748b;'>Bu doğrulama kodu <strong>5 dakika</strong> boyunca geçerlidir. Kodunuzu kimseyle paylaşmayınız.</p>"
                    + "    </div>"
                    + "    <div style='background-color: #f1f5f9; padding: 16px; text-align: center; font-size: 12px; color: #64748b; border-top: 1px solid #e2e8f0;'>"
                    + "      <p style='margin: 0;'>SANNY Akıllı Seyahat & Rezervasyon Asistanı</p>"
                    + "    </div>"
                    + "  </div>"
                    + "</body>"
                    + "</html>";

            helper.setText(html, true);
            mailSender.send(mimeMessage);
            log.info("[EmailService] OTP email sent successfully to {}", toEmail);
        } catch (Exception e) {
            log.error("[EmailService] Failed to send OTP email to {}: {}", toEmail, e.getMessage(), e);
        }
    }

    @Async
    public void sendReservationConfirmationEmail(Reservation reservation, String recipientEmail, String customerName) {
        sendReservationConfirmationEmail(reservation, recipientEmail, customerName, "tr");
    }

    @Async
    public void sendReservationConfirmationEmail(Reservation reservation, String recipientEmail, String customerName, String lang) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            log.warn("[EmailService] Cannot send reservation email: recipient email is missing");
            return;
        }

        String language = (lang != null && !lang.isBlank()) ? lang.toLowerCase() : "tr";

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom("sannydestek@gmail.com");
            helper.setTo(recipientEmail);

            String subject = getSubjectText(reservation, language);
            helper.setSubject(subject);

            String html = buildReservationHtmlTemplate(reservation, customerName, language);
            helper.setText(html, true);

            mailSender.send(mimeMessage);
            log.info("[EmailService] Asynchronous confirmation email sent successfully for PNR {}", reservation.getReservationNumber());
        } catch (Exception e) {
            log.error("[EmailService] Failed to send confirmation email for PNR {}: {}", reservation.getReservationNumber(), e.getMessage());
        }
    }

    private String getSubjectText(Reservation reservation, String lang) {
        String pnr = reservation.getReservationNumber() != null ? reservation.getReservationNumber() : "";
        switch (lang) {
            case "en":
                return "Sanny - Booking & Ticket Confirmation [" + pnr + "]";
            case "de":
                return "Sanny - Buchungs- & Ticketbestätigung [" + pnr + "]";
            case "ru":
                return "Sanny - Подтверждение бронирования и билета [" + pnr + "]";
            case "tr":
            default:
                return "Sanny - Rezervasyon ve Bilet Onayı [" + pnr + "]";
        }
    }

    private String buildReservationHtmlTemplate(Reservation reservation, String customerName, String lang) {
        boolean isFlight = "FLIGHT".equalsIgnoreCase(reservation.getType());
        String pnr = reservation.getReservationNumber() != null ? reservation.getReservationNumber() : "N/A";
        String itemName = reservation.getItemName() != null ? reservation.getItemName() : "-";
        String destination = reservation.getDestination() != null ? reservation.getDestination() : "-";
        String startDate = reservation.getStartDate() != null ? reservation.getStartDate().toString() : "-";
        String endDate = reservation.getEndDate() != null ? reservation.getEndDate().toString() : "-";
        String totalPrice = (reservation.getTotalPrice() != null ? String.format("%.2f", reservation.getTotalPrice()) : "0.00")
                + " " + (reservation.getCurrency() != null ? reservation.getCurrency() : "TRY");

        // Translations according to language
        String defaultName, subtitle, greeting, msgBody, pnrHeader, summaryHeader, passengerHeader;
        String typeLabel, serviceNameLabel, destLabel, startDateLabel, endDateLabel, totalAmountLabel;
        String nameCol, emailCol, phoneCol, noPassengers, footerLine1, footerLine2, paymentStatusLabel;

        switch (lang) {
            case "en":
                defaultName = "Valued Customer";
                subtitle = isFlight ? "Flight Booking Confirmation" : "Hotel Booking Confirmation";
                greeting = "Hello " + ((customerName != null && !customerName.isBlank()) ? customerName : defaultName) + ",";
                msgBody = "Your reservation has been successfully completed. Summary details are listed below:";
                pnrHeader = "Booking / PNR Code";
                summaryHeader = "Summary Details";
                passengerHeader = isFlight ? "Passenger Information" : "Guest Information";
                typeLabel = isFlight ? "✈️ Flight Ticket" : "🏨 Hotel Booking";
                serviceNameLabel = isFlight ? "Flight Name:" : "Hotel Name:";
                destLabel = isFlight ? "Route / Destination:" : "Location:";
                startDateLabel = isFlight ? "Departure Date:" : "Check-in Date:";
                endDateLabel = isFlight ? "Return Date:" : "Check-out Date:";
                totalAmountLabel = "Total Amount:";
                paymentStatusLabel = "Payment Status: APPROVED";
                nameCol = "Full Name";
                emailCol = "Email";
                phoneCol = "Phone";
                noPassengers = "No passenger details available.";
                footerLine1 = "This email was automatically generated by Sanny Booking System.";
                footerLine2 = "For any questions, contact us at";
                break;
            case "de":
                defaultName = "Sehr geehrter Kunde";
                subtitle = isFlight ? "Flugbuchungsbestätigung" : "Hotelbuchungsbestätigung";
                greeting = "Hallo " + ((customerName != null && !customerName.isBlank()) ? customerName : defaultName) + ",";
                msgBody = "Ihre Reservierung wurde erfolgreich abgeschlossen. Nachfolgend finden Sie eine Zusammenfassung:";
                pnrHeader = "Buchungs- / PNR-Code";
                summaryHeader = "Zusammenfassung";
                passengerHeader = isFlight ? "Passagierinformationen" : "Gästeinformationen";
                typeLabel = isFlight ? "✈️ Flugticket" : "🏨 Hotelbuchung";
                serviceNameLabel = isFlight ? "Flugbezeichnung:" : "Hotelname:";
                destLabel = "Reiseziel / Ort:";
                startDateLabel = isFlight ? "Abflugdatum:" : "Anreisedatum:";
                endDateLabel = isFlight ? "Rückflugdatum:" : "Abreisedatum:";
                totalAmountLabel = "Gesamtbetrag:";
                paymentStatusLabel = "Zahlungsstatus: BESTÄTIGT";
                nameCol = "Vollständiger Name";
                emailCol = "E-Mail";
                phoneCol = "Telefon";
                noPassengers = "Keine Passagierdaten vorhanden.";
                footerLine1 = "Diese E-Mail wurde automatisch vom Sanny Buchungssystem generiert.";
                footerLine2 = "Bei Fragen kontaktieren Sie uns unter";
                break;
            case "ru":
                defaultName = "Уважаемый клиент";
                subtitle = isFlight ? "Подтверждение бронирования авиабилета" : "Подтверждение бронирования отеля";
                greeting = "Здравствуйте, " + ((customerName != null && !customerName.isBlank()) ? customerName : defaultName) + "!";
                msgBody = "Ваше бронирование успешно завершено. Краткая информация приведена ниже:";
                pnrHeader = "Код бронирования / PNR";
                summaryHeader = "Детали бронирования";
                passengerHeader = isFlight ? "Информация о пассажирах" : "Информация о гостях";
                typeLabel = isFlight ? "✈️ Авиабилет" : "🏨 Бронирование отеля";
                serviceNameLabel = isFlight ? "Рейс:" : "Отель:";
                destLabel = "Маршрут / Локация:";
                startDateLabel = isFlight ? "Дата вылета:" : "Дата заезда:";
                endDateLabel = isFlight ? "Дата обратного вылета:" : "Дата выезда:";
                totalAmountLabel = "Итоговая сумма:";
                paymentStatusLabel = "Статус оплаты: ПОДТВЕРЖДЕНО";
                nameCol = "ФИО";
                emailCol = "Эл. почта";
                phoneCol = "Телефон";
                noPassengers = "Информация о пассажирах отсутствует.";
                footerLine1 = "Это письмо создано автоматически системой бронирования Sanny.";
                footerLine2 = "По любым вопросам связывайтесь с нами по адресу";
                break;
            case "tr":
            default:
                defaultName = "Değerli Müşterimiz";
                subtitle = isFlight ? "Uçuş Rezervasyonu Onay Belgesi" : "Otel Rezervasyonu Onay Belgesi";
                greeting = "Merhaba " + ((customerName != null && !customerName.isBlank()) ? customerName : defaultName) + ",";
                msgBody = "Rezervasyon işleminiz başarıyla tamamlanmıştır. Detaylar aşağıda özetlenmiştir:";
                pnrHeader = "Rezervasyon / PNR Kodu";
                summaryHeader = "Özet Detaylar";
                passengerHeader = isFlight ? "Yolcu Bilgileri" : "Misafir Bilgileri";
                typeLabel = isFlight ? "✈️ Uçak Bileti" : "🏨 Otel Rezervasyonu";
                serviceNameLabel = isFlight ? "Hizmet Adı:" : "Otel Adı:";
                destLabel = "Lokasyon:";
                startDateLabel = isFlight ? "Kalkış Tarihi:" : "Giriş Tarihi:";
                endDateLabel = isFlight ? "Dönüş Tarihi:" : "Çıkış Tarihi:";
                totalAmountLabel = "Toplam Tutar:";
                paymentStatusLabel = "Ödeme Durumu: ONAYLANDI";
                nameCol = "Ad Soyad";
                emailCol = "E-posta";
                phoneCol = "Telefon";
                noPassengers = "Yolcu detayı bulunmamaktadır.";
                footerLine1 = "Bu e-posta Sanny Otomatik Rezervasyon Sistemi tarafından gönderilmiştir.";
                footerLine2 = "Sorularınız için";
                break;
        }

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
            passengerRows.append("<tr><td colspan='3' style='padding: 8px 12px; color: #64748b;'>").append(noPassengers).append("</td></tr>");
        }

        return "<!DOCTYPE html>"
                + "<html>"
                + "<head><meta charset='UTF-8'></head>"
                + "<body style='font-family: Arial, Helvetica, sans-serif; background-color: #f8fafc; margin: 0; padding: 20px; color: #1e293b;'>"
                + "  <div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 16px; overflow: hidden; border: 1px solid #e2e8f0; box-shadow: 0 4px 12px rgba(0,0,0,0.05);'>"
                + "    <div style='background-color: #2563eb; padding: 24px; text-align: center; color: #ffffff;'>"
                + "      <h1 style='margin: 0; font-size: 24px; font-weight: bold;'>SANNY TRAVEL</h1>"
                + "      <p style='margin: 4px 0 0 0; font-size: 14px; opacity: 0.9;'>" + subtitle + "</p>"
                + "    </div>"
                + "    <div style='padding: 32px;'>"
                + "      <h2 style='font-size: 18px; color: #0f172a; margin-top: 0;'>" + greeting + "</h2>"
                + "      <p style='font-size: 14px; line-height: 1.6; color: #475569;'>" + msgBody + "</p>"
                + "      <div style='background-color: #eff6ff; border: 1px solid #bfdbfe; border-radius: 12px; padding: 16px; margin: 20px 0; text-align: center;'>"
                + "        <span style='font-size: 12px; color: #1e40af; text-transform: uppercase; font-weight: bold; letter-spacing: 1px;'>" + pnrHeader + "</span>"
                + "        <div style='font-size: 26px; font-weight: 800; color: #1d4ed8; margin-top: 4px;'>" + pnr + "</div>"
                + "      </div>"
                + "      <div style='background-color: #f0fdf4; border: 1px solid #bbf7d0; border-radius: 8px; padding: 10px; margin-bottom: 20px; text-align: center; color: #166534; font-weight: bold; font-size: 13px;'>"
                +          paymentStatusLabel
                + "      </div>"
                + "      <h3 style='font-size: 15px; color: #1e293b; border-bottom: 2px solid #f1f5f9; padding-bottom: 8px; margin-top: 24px;'>" + summaryHeader + "</h3>"
                + "      <table style='width: 100%; font-size: 14px; border-collapse: collapse; margin-bottom: 20px;'>"
                + "        <tr><td style='padding: 8px 0; color: #64748b; width: 40%;'>" + typeLabel.split(" ")[0] + " Type:</td><td style='padding: 8px 0; font-weight: bold;'>" + typeLabel + "</td></tr>"
                + "        <tr><td style='padding: 8px 0; color: #64748b;'>" + serviceNameLabel + "</td><td style='padding: 8px 0; font-weight: bold;'>" + itemName + "</td></tr>"
                + "        <tr><td style='padding: 8px 0; color: #64748b;'>" + destLabel + "</td><td style='padding: 8px 0; font-weight: bold;'>" + destination + "</td></tr>"
                + "        <tr><td style='padding: 8px 0; color: #64748b;'>" + startDateLabel + "</td><td style='padding: 8px 0; font-weight: bold;'>" + startDate + "</td></tr>"
                + "        <tr><td style='padding: 8px 0; color: #64748b;'>" + endDateLabel + "</td><td style='padding: 8px 0; font-weight: bold;'>" + endDate + "</td></tr>"
                + "        <tr><td style='padding: 8px 0; color: #64748b;'>" + totalAmountLabel + "</td><td style='padding: 8px 0; font-weight: 800; color: #2563eb; font-size: 16px;'>" + totalPrice + "</td></tr>"
                + "      </table>"
                + "      <h3 style='font-size: 15px; color: #1e293b; border-bottom: 2px solid #f1f5f9; padding-bottom: 8px; margin-top: 24px;'>" + passengerHeader + "</h3>"
                + "      <table style='width: 100%; font-size: 13px; border-collapse: collapse; text-align: left;'>"
                + "        <thead><tr style='background-color: #f8fafc; color: #475569;'><th style='padding: 8px 12px;'>" + nameCol + "</th><th style='padding: 8px 12px;'>" + emailCol + "</th><th style='padding: 8px 12px;'>" + phoneCol + "</th></tr></thead>"
                + "        <tbody>" + passengerRows.toString() + "</tbody>"
                + "      </table>"
                + "    </div>"
                + "    <div style='background-color: #f1f5f9; padding: 16px; text-align: center; font-size: 12px; color: #64748b; border-top: 1px solid #e2e8f0;'>"
                + "      <p style='margin: 0;'>" + footerLine1 + "</p>"
                + "      <p style='margin: 4px 0 0 0;'>" + footerLine2 + " <a href='mailto:sannydestek@gmail.com' style='color: #2563eb;'>sannydestek@gmail.com</a></p>"
                + "    </div>"
                + "  </div>"
                + "</body>"
                + "</html>";
    }
}