package com.santsg.tourvisio.controller;

import com.santsg.tourvisio.dto.FlightSearchRequest;
import com.santsg.tourvisio.dto.FlightSearchResponseItem;
import com.santsg.tourvisio.service.FlightSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/flights")
@Tag(name = "Flight Search Controller", description = "Endpoints for proxying TourVisio flight searches")
public class FlightSearchController {

    private final FlightSearchService flightSearchService;

    public FlightSearchController(FlightSearchService flightSearchService) {
        this.flightSearchService = flightSearchService;
    }

    @PostMapping("/search")
    @Operation(summary = "Search flights", description = "Forwards criteria to TourVisio API and returns matching flights")
    public ResponseEntity<List<FlightSearchResponseItem>> searchFlights(@Valid @RequestBody FlightSearchRequest request) {
        List<FlightSearchResponseItem> results = flightSearchService.searchFlights(request);
        return ResponseEntity.ok(results);
    }
}
