package com.santsg.tourvisio.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReservationRequest {

    @NotBlank(message = "Reservation type cannot be blank (e.g., HOTEL, FLIGHT)")
    private String type;

    @NotBlank(message = "Item name cannot be blank")
    private String itemName;

    @NotBlank(message = "Destination cannot be blank")
    private String destination;

    @NotNull(message = "Start date cannot be null")
    private LocalDate startDate;

    @NotNull(message = "End date cannot be null")
    private LocalDate endDate;

    @NotNull(message = "Total price cannot be null")
    @Min(value = 0, message = "Total price must be non-negative")
    private Double totalPrice;

    @NotBlank(message = "Currency cannot be blank")
    private String currency;

    private String chatSessionId;

    @NotEmpty(message = "Reservation must have at least one passenger")
    @Valid
    private List<PassengerRequest> passengers;

    @AssertTrue(message = "First passenger contact information is required.")
    public boolean isPrimaryContactValid() {
        if (passengers == null || passengers.isEmpty()) {
            return true;
        }

        PassengerRequest primary = passengers.get(0);

        boolean hasEmail = primary.getEmail() != null && !primary.getEmail().isBlank();
        boolean hasPhone = primary.getPhoneNumber() != null && !primary.getPhoneNumber().isBlank();

        return hasEmail && hasPhone;
    }

    private String imageUrl;
}
