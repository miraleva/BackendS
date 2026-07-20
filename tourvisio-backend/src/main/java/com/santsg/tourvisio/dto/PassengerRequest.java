package com.santsg.tourvisio.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PassengerRequest {

    @NotBlank(message = "First name cannot be blank")
    private String firstName;

    @NotBlank(message = "Last name cannot be blank")
    private String lastName;

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email cannot be blank")
    private String email;

    @NotBlank(message = "Phone number cannot be blank")
    private String phoneNumber;

    @NotBlank(message = "Identity number (TC No) cannot be blank")
    private String identityNumber;

    @NotNull(message = "Birth date cannot be null")
    private LocalDate birthDate;

    @NotBlank(message = "Gender cannot be blank")
    private String gender;

    @NotBlank(message = "Nationality cannot be blank")
    private String nationality;

    // 5-argument constructor for test compatibility
    public PassengerRequest(String firstName, String lastName, String email, String phoneNumber, String identityNumber) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.identityNumber = identityNumber;
        this.birthDate = LocalDate.of(1990, 1, 1);
        this.gender = "MR";
        this.nationality = "TR";
    }
}
