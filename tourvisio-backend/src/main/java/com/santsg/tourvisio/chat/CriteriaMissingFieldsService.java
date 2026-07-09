package com.santsg.tourvisio.chat;

import com.santsg.tourvisio.client.AIProviderClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Biriktirilmiş {@link SearchCriteria}'ya bakarak hangi zorunlu alanların
 * hâlâ eksik olduğunu hesaplar.
 *
 * <p>Mevcut {@code MissingParameterService}'den farklı olarak bu sınıf
 * ham metin yerine zaten parse edilmiş {@code SearchCriteria} nesnesini
 * inceler. Böylece çok turlu konuşmalarda önceki turda doldurulan alanlar
 * "eksik" sayılmaz.</p>
 */
@Service
public class CriteriaMissingFieldsService {

    private final AIProviderClient aiProviderClient;

    public CriteriaMissingFieldsService(AIProviderClient aiProviderClient) {
        this.aiProviderClient = aiProviderClient;
    }

    /**
     * {@code criteria} içindeki {@code null} / boş zorunlu alanları listeler.
     *
     * @param criteria Biriktirilmiş arama kriterleri
     * @return Kullanıcıya gösterilecek Türkçe alan adları listesi; tamsa boş liste
     */
    public List<String> getMissingFields(SearchCriteria criteria) {
        if (criteria == null) return List.of();

        String searchType = criteria.getSearchType();
        List<String> missing = new ArrayList<>();

        if ("HOTEL_SEARCH".equals(searchType)) {
            if (isBlank(criteria.getLocationOrHotelName())) missing.add("konum veya otel adı");
            if (criteria.getCheckInDate()  == null)          missing.add("giriş tarihi");
            if (criteria.getCheckOutDate() == null)          missing.add("çıkış tarihi");
            if (criteria.getAdultCount()   == null)          missing.add("yetişkin sayısı");
            if (criteria.getChildCount()   != null
                    && criteria.getChildCount() > 0
                    && criteria.getChildAges().isEmpty())     missing.add("çocuk yaşları");
            if (isBlank(criteria.getCurrency()))              missing.add("para birimi");

        } else if ("FLIGHT_SEARCH".equals(searchType)) {
            if (isBlank(criteria.getDepartureLocation())) missing.add("kalkış noktası");
            if (isBlank(criteria.getArrivalLocation()))   missing.add("varış noktası");
            if (criteria.getDepartureDate()  == null)     missing.add("gidiş tarihi");
            if (criteria.getPassengerCount() == null)     missing.add("yolcu sayısı");
            if (isBlank(criteria.getTripType()))          missing.add("tek yön / gidiş-dönüş");
            if ("ROUND_TRIP".equalsIgnoreCase(criteria.getTripType()) && criteria.getReturnDate() == null) {
                missing.add("dönüş tarihi");
            }
            if (isBlank(criteria.getCurrency()))          missing.add("para birimi");
        }

        return missing;
    }

    /**
     * Eksik alanları kullanıcıya gösterilecek tek bir Türkçe soruya dönüştürür.
     */
    public String buildPrompt(List<String> missingFields) {
        return buildPrompt(missingFields, null);
    }

    /**
     * Eksik alanları kullanıcıya gösterilecek tek bir soruya dönüştürür.
     */
    public String buildPrompt(List<String> missingFields, SearchCriteria criteria) {
        if (missingFields.isEmpty()) return "";

        String lang = (criteria != null && criteria.getPreferredLanguage() != null) ? criteria.getPreferredLanguage() : "Turkish";
        String country = (criteria != null && criteria.getCountry() != null) ? criteria.getCountry() : "Turkey";

        // AI ile soru sorma (API key tanımlıysa)
        try {
            String prompt = String.format(
                    "The user needs to supply the following missing information for their travel search: %s.\n\n" +
                    "Write a short, polite and natural question in the official/most common language of %s (%s) asking the user to provide this missing information.\n" +
                    "Return ONLY the question itself, no explanations.\n" +
                    "Question:",
                    String.join(", ", missingFields), country, lang
            );

            String response = aiProviderClient.complete(prompt);
            if (response != null && !response.trim().startsWith("[MOCK]")) {
                return response.trim();
            }
        } catch (Exception e) {
            // Hata durumunda fallback
        }

        if ("English".equalsIgnoreCase(lang) || "en".equalsIgnoreCase(lang)) {
            if (missingFields.size() == 1) {
                return "Could you specify the " + missingFields.get(0) + " to perform the search?";
            }
            return "Could you specify the following missing information: " + String.join(", ", missingFields) + "?";
        }

        if (missingFields.size() == 1) {
            return "Arama yapabilmem için " + missingFields.get(0) + " bilgisini de belirtir misiniz?";
        }
        return "Arama yapabilmem için şu eksik bilgileri de belirtir misiniz: "
                + String.join(", ", missingFields) + "?";
    }

    // ─────────────────────────────────────────────────────────────────────────

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
