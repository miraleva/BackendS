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
        if (missingFields.isEmpty()) return "";

        // AI ile soru sorma (API key tanımlıysa)
        try {
            String prompt = """
                    Kullanıcının otel veya uçak araması yapabilmesi için şu zorunlu bilgileri vermesi gerekiyor:
                    Zorunlu Eksik Bilgiler: %s
                    
                    Kullanıcıya bu eksik bilgileri nazikçe, doğal dilde ve Türkçe olarak soran kısa ve kibar bir soru cümlesi yaz.
                    Sadece soru cümlesini dön (başka hiçbir açıklayıcı metin ekleme).
                    Soru:""".formatted(String.join(", ", missingFields));

            String response = aiProviderClient.complete(prompt);
            if (response != null && !response.trim().startsWith("[MOCK]")) {
                return response.trim();
            }
        } catch (Exception e) {
            // Hata durumunda fallback
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
