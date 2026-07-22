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
            boolean childAgesPending = criteria.getChildCount() != null
                    && criteria.getChildCount() > 0
                    && criteria.getChildAges().isEmpty();
            boolean infantAgesPending = criteria.getInfantCount() != null
                    && criteria.getInfantCount() > 0
                    && criteria.getInfantAges().isEmpty();

            if (isBlank(criteria.getLocationOrHotelName())) missing.add("konum veya otel adı");
            if (criteria.getCheckInDate()  == null)          missing.add("giriş tarihi");
            if (criteria.getCheckOutDate() == null)          missing.add("çıkış tarihi");
            // Yetişkin sayısı, çocuk/bebek yaşları eksikken AYNI TURDA sorulmaz: ikisi de
            // sayısal olduğu için "5 6" gibi bare bir cevapta hangisinin hangi alana ait
            // olduğu belirsizleşiyor (örn. "2 çocuk"tan sonra yaş soruluyor, kullanıcı "5 6"
            // dediğinde bu iki çocuğun yaşı, ama yetişkin sayısı sorusu da aynı anda
            // sorulduğu için modelde "5"i yetişkin sayısına da yazma eğilimi oluşuyor).
            // Yaşlar netleşmeden yetişkin sayısını sormayıp bir sonraki tura bırakıyoruz.
            if (!childAgesPending && !infantAgesPending && criteria.getAdultCount() == null) {
                missing.add("yetişkin sayısı");
            }
            if (childAgesPending)                             missing.add("çocuk yaşları");
            if (infantAgesPending)                             missing.add("bebek yaşları");
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
