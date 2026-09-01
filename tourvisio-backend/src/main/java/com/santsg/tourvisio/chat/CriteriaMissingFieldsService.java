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
        if (searchType == null || "UNKNOWN".equalsIgnoreCase(searchType)) {
            if (criteria.getDepartureLocation() != null || criteria.getArrivalLocation() != null || criteria.getDepartureDate() != null) {
                searchType = "FLIGHT_SEARCH";
            } else if (criteria.getLocationOrHotelName() != null || criteria.getCheckInDate() != null) {
                searchType = "HOTEL_SEARCH";
            }
        }

        List<String> missing = new ArrayList<>();

        if ("HOTEL_SEARCH".equals(searchType)) {
            boolean childAgesPending = criteria.getChildCount() != null
                    && criteria.getChildCount() > 0
                    && (criteria.getChildAges() == null || criteria.getChildAges().isEmpty() || criteria.getChildAges().size() != criteria.getChildCount());
            boolean infantAgesPending = criteria.getInfantCount() != null
                    && criteria.getInfantCount() > 0
                    && (criteria.getInfantAges() == null || criteria.getInfantAges().isEmpty() || criteria.getInfantAges().size() != criteria.getInfantCount());

            // Çocuk/bebek yaşları birinci önceliktir — yaşlar öğrenilmeden tarihlere geçilmez
            if (childAgesPending)                             missing.add("çocuk yaşları");
            if (infantAgesPending)                             missing.add("bebek yaşları");

            boolean isFlexibleDates = Boolean.TRUE.equals(criteria.getFlexibleDates());

            if (isBlank(criteria.getLocationOrHotelName())) missing.add("konum veya otel adı");
            if (!isFlexibleDates && criteria.getCheckInDate()  == null)          missing.add("giriş tarihi");
            if (!isFlexibleDates && criteria.getCheckOutDate() == null)          missing.add("çıkış tarihi");
            if (criteria.getRoomCount() == null)             missing.add("oda sayısı");
            if (criteria.getChildCount() == null)            missing.add("çocuk sayısı");
            if (!childAgesPending && !infantAgesPending && !isFlexibleDates && criteria.getAdultCount() == null && criteria.getPassengerCount() == null) {
                missing.add("yetişkin sayısı");
            }


        } else if ("FLIGHT_SEARCH".equals(searchType)) {
            boolean childAgesPending = criteria.getChildCount() != null
                    && criteria.getChildCount() > 0
                    && (criteria.getChildAges() == null || criteria.getChildAges().isEmpty() || criteria.getChildAges().size() != criteria.getChildCount());
            boolean infantAgesPending = criteria.getInfantCount() != null
                    && criteria.getInfantCount() > 0
                    && (criteria.getInfantAges() == null || criteria.getInfantAges().isEmpty() || criteria.getInfantAges().size() != criteria.getInfantCount());

            if (childAgesPending)  missing.add("çocuk yaşları");
            if (infantAgesPending) missing.add("bebek kaç aylık");

            boolean isFlexibleDates = Boolean.TRUE.equals(criteria.getFlexibleDates());

            if (isBlank(criteria.getDepartureLocation())) missing.add("kalkış noktası");
            if (isBlank(criteria.getArrivalLocation()))   missing.add("varış noktası");
            if (!isFlexibleDates && criteria.getDepartureDate()  == null)     missing.add("gidiş tarihi");
            if (!isFlexibleDates && criteria.getPassengerCount() == null && criteria.getAdultCount() == null) missing.add("yolcu sayısı");
            if (!isFlexibleDates && isBlank(criteria.getTripType()))          missing.add("tek yön / gidiş-dönüş");
            if ("ROUND_TRIP".equalsIgnoreCase(criteria.getTripType()) && criteria.getReturnDate() == null) {
                missing.add("dönüş tarihi");
            }
        }


        return missing;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
