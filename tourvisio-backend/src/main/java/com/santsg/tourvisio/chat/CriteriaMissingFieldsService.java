package com.santsg.tourvisio.chat;

import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

/**
 * Biriktirilmiş {@link SearchCriteria}'ya bakarak hangi zorunlu alanların
 * hâlâ eksik olduğunu hesaplar.
 */
@Service
public class CriteriaMissingFieldsService {

    public CriteriaMissingFieldsService() {
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

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
