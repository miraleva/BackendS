package com.santsg.tourvisio.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Health Controller", description = "Endpoints for monitoring application health")
public class HealthController {

    @GetMapping("/health")
    @Operation(summary = "Get application health status", description = "Returns a simple status message indicating that the application is running.")
    public String getHealth() {
        return "Uygulama çalışıyor";
    }
}
