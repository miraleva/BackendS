package com.santsg.tourvisio.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PassengerRequest {

    @NotBlank(message = "First name cannot be blank")
    @Size(max = 50, message = "First name cannot exceed 50 characters")
    private String firstName;

    @NotBlank(message = "Last name cannot be blank")
    @Size(max = 50, message = "Last name cannot exceed 50 characters")
    private String lastName;

    @Email(message = "Invalid email format")
    @Size(max = 100, message = "Email cannot exceed 100 characters")
    private String email;

    @Size(max = 20, message = "Phone number cannot exceed 20 characters")
    private String phoneNumber;

    @NotBlank(message = "Identity number (TC No) cannot be blank")
    @Size(max = 30, message = "Identity number cannot exceed 30 characters")
    private String identityNumber;

    @NotNull(message = "Birth date cannot be null")
    private LocalDate birthDate;

    @NotBlank(message = "Gender cannot be blank")
    @Size(max = 10, message = "Gender cannot exceed 10 characters")
    private String gender;

    @NotBlank(message = "Nationality cannot be blank")
    @Size(max = 10, message = "Nationality cannot exceed 10 characters")
    private String nationality;

    // 5-argument constructor for test compatibility
    public PassengerRequest(String firstName, String lastName, String email, String phoneNumber,
            String identityNumber) {
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

