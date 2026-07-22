package com.santsg.tourvisio.chat;

import org.springframework.stereotype.Component;
import java.time.LocalDate;

@Component
public class SearchCriteriaValidator {

    public static class ValidationResult {
        private final boolean valid;
        private final String errorType;

        public ValidationResult(boolean valid, String errorType) {
            this.valid = valid;
            this.errorType = errorType;
        }

        public boolean isValid() { return valid; }
        public String getErrorType() { return errorType; }
    }

    public ValidationResult validate(SearchCriteria criteria) {
        LocalDate today = LocalDate.now();

        LocalDate maxFutureDate = today.plusYears(MAX_YEARS_AHEAD);

        // Hotel Dates
        if (criteria.getCheckInDate() != null) {
            if (criteria.getCheckInDate().isBefore(today)) {
                return new ValidationResult(false, "DATE_PAST");
            }
            if (criteria.getCheckInDate().isAfter(maxFutureDate)
                    || (criteria.getCheckOutDate() != null && criteria.getCheckOutDate().isAfter(maxFutureDate))) {
                return new ValidationResult(false, "DATE_TOO_FAR");
            }
            if (criteria.getCheckOutDate() != null && !criteria.getCheckOutDate().isAfter(criteria.getCheckInDate())) {
                return new ValidationResult(false, "DATE_MISMATCH");
            }
        }

        // Flight Dates
        if (criteria.getDepartureDate() != null) {
            if (criteria.getDepartureDate().isBefore(today)) {
                return new ValidationResult(false, "DATE_PAST");
            }
            if (criteria.getDepartureDate().isAfter(maxFutureDate)
                    || (criteria.getReturnDate() != null && criteria.getReturnDate().isAfter(maxFutureDate))) {
                return new ValidationResult(false, "DATE_TOO_FAR");
            }
            if (criteria.getReturnDate() != null && !criteria.getReturnDate().isAfter(criteria.getDepartureDate())) {
                return new ValidationResult(false, "DATE_MISMATCH");
            }
        }

        // Child Ages
        if (criteria.getChildAges() != null) {
            boolean hasNegativeChild = criteria.getChildAges().stream().anyMatch(age -> age < 0);
            if (hasNegativeChild) {
                return new ValidationResult(false, "CHILD_AGE_TOO_HIGH"); // reuse for simplicity, or add new
            }
        }

        // Negatif yolcu/misafir sayıları — modelin garip/beklenmedik cevaplar üretmesine
        // (ör. anlamsız bir dile geçmesine) yol açan kaynak; erken ve net biçimde reddedilir.
        if (isNegative(criteria.getAdultCount()) || isNegative(criteria.getChildCount())
                || isNegative(criteria.getInfantCount()) || isNegative(criteria.getPassengerCount())
                || isNegative(criteria.getRoomCount())) {
            return new ValidationResult(false, "NEGATIVE_COUNT");
        }

        // Adults
        if ("HOTEL_SEARCH".equals(criteria.getSearchType()) && criteria.getAdultCount() != null && criteria.getAdultCount() == 0) {
            return new ValidationResult(false, "NO_ADULTS");
        }

        // Uçakta 0 yolcu ile arama yapılması (hotel'deki NO_ADULTS'un uçak karşılığı) —
        // aksi hâlde 0 yolcu ile TourVisio'ya boş/anlamsız bir arama gidiyordu.
        if ("FLIGHT_SEARCH".equals(criteria.getSearchType()) && criteria.getPassengerCount() != null && criteria.getPassengerCount() == 0) {
            return new ValidationResult(false, "NO_ADULTS");
        }

        // Üst sınırlar: otel için toplam misafir (yetişkin+çocuk+bebek) en fazla 8,
        // uçak için yolcu sayısı en fazla 9 kabul edilir.
        if ("HOTEL_SEARCH".equals(criteria.getSearchType())) {
            int totalGuests = nz(criteria.getAdultCount()) + nz(criteria.getChildCount()) + nz(criteria.getInfantCount());
            if (totalGuests > MAX_HOTEL_GUESTS) {
                return new ValidationResult(false, "TOO_MANY_GUESTS");
            }
            // Oda sayısı kişi sayısını (veya makul bir üst sınırı) aşamaz — aksi hâlde
            // her oda TourVisio'ya TAM yetişkin sayısıyla gönderiliyor (bkz.
            // TourVisioRequestMapper), yani "2 kişi, 50 oda" aslında 100 kişilik yer
            // aramaya dönüşüyordu.
            if (criteria.getRoomCount() != null && totalGuests > 0
                    && (criteria.getRoomCount() > totalGuests || criteria.getRoomCount() > MAX_ROOM_COUNT)) {
                return new ValidationResult(false, "TOO_MANY_ROOMS");
            }
        }
        if ("FLIGHT_SEARCH".equals(criteria.getSearchType()) && nz(criteria.getPassengerCount()) > MAX_FLIGHT_PASSENGERS) {
            return new ValidationResult(false, "TOO_MANY_PASSENGERS");
        }

        return new ValidationResult(true, null);
    }

    private static final int MAX_HOTEL_GUESTS = 8;
    private static final int MAX_FLIGHT_PASSENGERS = 9;
    private static final int MAX_ROOM_COUNT = 4;
    /** Gerçekçi bir rezervasyon penceresi — "9999 yılı" gibi anlamsız uzak tarihleri reddeder. */
    private static final int MAX_YEARS_AHEAD = 2;

    private boolean isNegative(Integer value) {
        return value != null && value < 0;
    }

    private int nz(Integer value) {
        return value != null ? value : 0;
    }
}
