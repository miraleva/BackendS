package com.santsg.tourvisio.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HotelSearchRequest {

    @NotBlank(message = "locationOrHotelName is required")
    private String locationOrHotelName;

    @NotNull(message = "checkInDate is required")
    private LocalDate checkInDate;

    @NotNull(message = "checkOutDate is required")
    private LocalDate checkOutDate;

    @NotNull(message = "adultCount is required")
    @Min(value = 1, message = "adultCount must be at least 1")
    private Integer adultCount;

    @NotBlank(message = "currency is required")
    private String currency;

    // TourVisio ek alanlar (opsiyonel)
    private Integer childCount;
    private List<Integer> childAges;
    private Integer roomCount;
<<<<<<< HEAD
    private String nationality;  // e.g., "TR", "DE"
=======
    private String nationality; // e.g., "TR", "DE"
>>>>>>> 34740d24e8d256b34bad26dc32a4569e2b91c759

    // Additional optional filters
    private String region;
    private Double minPrice;
    private Double maxPrice;
    private Integer starCount;
    private String pensionType; // e.g., "FULL", "HALF"
    private Boolean sortByCheapest; // true to sort ascending by price

    // Custom validation to ensure checkOutDate after checkInDate
    @jakarta.validation.constraints.AssertTrue(message = "checkOutDate must be after checkInDate")
    public boolean isCheckOutAfterCheckIn() {
        if (checkInDate == null || checkOutDate == null) {
            return true; // other @NotNull will handle null cases
        }
        return checkOutDate.isAfter(checkInDate);
    }
}
