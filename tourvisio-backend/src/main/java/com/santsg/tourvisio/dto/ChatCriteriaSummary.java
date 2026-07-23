package com.santsg.tourvisio.dto;

import com.santsg.tourvisio.chat.SearchCriteria;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Sohbette o ana kadar toplanmış arama kriterlerinin frontend'e özetlenmiş
 * hâli. Frontend, sağdaki "Canlı Rezervasyon" panelini bu alanlardan
 * doldurur — kırılgan metin ayrıştırma yerine backend'in çözdüğü kriterleri
 * doğrudan kullanır.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "O ana kadar toplanmış arama kriterlerinin özeti")
public class ChatCriteriaSummary {

    private String locationOrHotelName;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private Integer adultCount;
    private Integer childCount;
    private List<Integer> childAges;
    private Integer infantCount;
    private List<Integer> infantAges;

    private String departureLocation;
    private String arrivalLocation;
    private LocalDate departureDate;
    private LocalDate returnDate;
    private Integer passengerCount;
    private String tripType;

    private String currency;

    private Double maxPrice;
    private Double minPrice;
    private Integer minStars;

    public static ChatCriteriaSummary from(SearchCriteria criteria) {
        if (criteria == null) {
            return null;
        }
        return ChatCriteriaSummary.builder()
                .locationOrHotelName(criteria.getLocationOrHotelName())
                .checkInDate(criteria.getCheckInDate())
                .checkOutDate(criteria.getCheckOutDate())
                .adultCount(criteria.getAdultCount())
                .childCount(criteria.getChildCount())
                .childAges(criteria.getChildAges())
                .infantCount(criteria.getInfantCount())
                .infantAges(criteria.getInfantAges())
                .departureLocation(criteria.getDepartureLocation())
                .arrivalLocation(criteria.getArrivalLocation())
                .departureDate(criteria.getDepartureDate())
                .returnDate(criteria.getReturnDate())
                .passengerCount(criteria.getPassengerCount())
                .tripType(criteria.getTripType())
                .currency(criteria.getCurrency())
                .maxPrice(criteria.getMaxPrice())
                .minPrice(criteria.getMinPrice())
                .minStars(criteria.getMinStars())
                .build();
    }
}
