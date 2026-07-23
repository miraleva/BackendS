package com.santsg.tourvisio.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AdminLoginRequest {

    @NotBlank(message = "Password cannot be empty")
    @Size(min = 4, max = 100, message = "Password must be between 4 and 100 characters")
    private String password;

    // Getter ve Setter metotları
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}