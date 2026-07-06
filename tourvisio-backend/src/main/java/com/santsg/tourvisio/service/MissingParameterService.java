package com.santsg.tourvisio.service;

import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class MissingParameterService {

    public List<String> getMissingParameters(String intent, String userMessage) {
        List<String> missingFields = new ArrayList<>();
        if (userMessage == null) {
            userMessage = "";
        }
        String lowerMsg = userMessage.toLowerCase(Locale.forLanguageTag("tr-TR"));

        if ("HOTEL_SEARCH".equals(intent)) {
            // Required: lokasyon, giriş tarihi, çıkış tarihi, yetişkin sayısı, para birimi

            // 1. Lokasyon: Check for city names or "lokasyon", "otel konumu", "şehir"
            boolean hasLocation = hasWord(lowerMsg, "lokasyon") || hasWord(lowerMsg, "şehir") || hasWord(lowerMsg, "sehir") ||
                    lowerMsg.contains("antalya") || lowerMsg.contains("istanbul") || lowerMsg.contains("izmir") || 
                    lowerMsg.contains("ankara") || lowerMsg.contains("bodrum") || lowerMsg.contains("marmaris") || 
                    lowerMsg.contains("fethiye") || lowerMsg.contains("alanya") || lowerMsg.contains("paris") ||
                    lowerMsg.contains("londra") || lowerMsg.contains("roma");
            if (!hasLocation) {
                missingFields.add("lokasyon");
            }

            // 2. Giriş Tarihi: check for "giriş", "giris", "checkin", "check-in"
            boolean hasCheckIn = lowerMsg.contains("giriş") || lowerMsg.contains("giris") || 
                                 lowerMsg.contains("checkin") || lowerMsg.contains("check-in") ||
                                 lowerMsg.contains("başlangıç") || lowerMsg.contains("baslangic");
            if (!hasCheckIn) {
                missingFields.add("giriş tarihi");
            }

            // 3. Çıkış Tarihi: check for "çıkış", "cikis", "checkout", "check-out"
            boolean hasCheckOut = lowerMsg.contains("çıkış") || lowerMsg.contains("cikis") || 
                                  lowerMsg.contains("checkout") || lowerMsg.contains("check-out") ||
                                  lowerMsg.contains("bitiş") || lowerMsg.contains("bitis");
            if (!hasCheckOut) {
                missingFields.add("çıkış tarihi");
            }

            // 4. Yetişkin Sayısı: check for "yetişkin", "yetiskin", "kişi", "kisi", "adult" (with some numbers or explicitly)
            boolean hasAdults = lowerMsg.contains("yetişkin") || lowerMsg.contains("yetiskin") || 
                                lowerMsg.contains("kişi") || lowerMsg.contains("kisi") || 
                                lowerMsg.contains("adult");
            if (!hasAdults) {
                missingFields.add("yetişkin sayısı");
            }

            // 5. Para Birimi: check for "tl", "try", "euro", "dolar", "usd", "eur", "lira" as full words
            boolean hasCurrency = hasWord(lowerMsg, "tl") || hasWord(lowerMsg, "try") || 
                                  hasWord(lowerMsg, "euro") || hasWord(lowerMsg, "dolar") || 
                                  hasWord(lowerMsg, "usd") || hasWord(lowerMsg, "eur") || 
                                  hasWord(lowerMsg, "lira") || lowerMsg.contains("para birimi");
            if (!hasCurrency) {
                missingFields.add("para birimi");
            }

        } else if ("FLIGHT_SEARCH".equals(intent)) {
            // Required: kalkış noktası, varış noktası, gidiş tarihi, yolcu sayısı, tek yön/gidiş-dönüş bilgisi

            // 1. Kalkış Noktası: check for city name + "dan/den/tan/ten" suffix, or "kalkış", "nereden"
            boolean hasDeparture = lowerMsg.contains("kalkış") || lowerMsg.contains("kalkis") || 
                                   lowerMsg.contains("nereden") || 
                                   Pattern.compile("\\b\\w+('(?:dan|den|tan|ten)|dan|den|tan|ten)\\b").matcher(lowerMsg).find();
            if (!hasDeparture) {
                missingFields.add("kalkış noktası");
            }

            // 2. Varış Noktası: check for city name + "a/e/ya/ye" suffix, or "varış", "nereye"
            boolean hasArrival = lowerMsg.contains("varış") || lowerMsg.contains("varis") || 
                                 lowerMsg.contains("nereye") || 
                                 Pattern.compile("\\b\\w+('(?:ya|ye|a|e)|ya|ye)\\b").matcher(lowerMsg).find();
            
            // Special rule: if user specified X'ten Y'ye, both regex will match.
            // Let's also check if user has word 'hedef' or specific destination indicators.
            if (!hasArrival) {
                missingFields.add("varış noktası");
            }

            // 3. Gidiş Tarihi: check for "gidiş", "gidis", "tarih", "uçuş günü", "ucus gunu"
            boolean hasFlightDate = lowerMsg.contains("gidiş") || lowerMsg.contains("gidis") || 
                                    lowerMsg.contains("tarih") || lowerMsg.contains("gün") || 
                                    lowerMsg.contains("gun");
            if (!hasFlightDate) {
                missingFields.add("gidiş tarihi");
            }

            // 4. Yolcu Sayısı: check for "yolcu", "kişi", "kisi", "passenger", "bilet sayısı" (exclude general "bilet" unless it specifies count)
            boolean hasPassengers = lowerMsg.contains("yolcu") || lowerMsg.contains("kişi") || 
                                    lowerMsg.contains("kisi") || lowerMsg.contains("passenger") ||
                                    lowerMsg.contains("kişilik") || lowerMsg.contains("kisilik");
            if (!hasPassengers) {
                missingFields.add("yolcu sayısı");
            }

            // 5. Tek yön/Gidiş-dönüş bilgisi: check for trip type keywords
            boolean hasTripType = lowerMsg.contains("tek yön") || lowerMsg.contains("tek yon") || 
                                  lowerMsg.contains("gidiş dönüş") || lowerMsg.contains("gidis donus") || 
                                  lowerMsg.contains("gidiş-dönüş") || lowerMsg.contains("gidis-donus") || 
                                  lowerMsg.contains("gidiş ve dönüş") || lowerMsg.contains("tek-yön");
            if (!hasTripType) {
                missingFields.add("tek yön/gidiş-dönüş bilgisi");
            }
        }

        return missingFields;
    }

    private boolean hasWord(String source, String word) {
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(source).find();
    }

    public String generateMissingFieldsPrompt(List<String> missingFields) {
        if (missingFields.isEmpty()) {
            return "";
        }
        return "Arama işlemini gerçekleştirebilmem için lütfen şu eksik bilgileri de belirtin: " + String.join(", ", missingFields);
    }
}
