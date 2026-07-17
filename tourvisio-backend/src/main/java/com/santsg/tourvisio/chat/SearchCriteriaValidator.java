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

        // Hotel Dates
        if (criteria.getCheckInDate() != null) {
            if (criteria.getCheckInDate().isBefore(today)) {
                return new ValidationResult(false, "DATE_PAST");
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

        // Adults
        if ("HOTEL_SEARCH".equals(criteria.getSearchType()) && criteria.getAdultCount() != null && criteria.getAdultCount() == 0) {
            return new ValidationResult(false, "NO_ADULTS");
        }

        return new ValidationResult(true, null);
    }
}
