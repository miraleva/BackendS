package com.santsg.tourvisio.controller;

import com.santsg.tourvisio.dto.HotelSearchRequest;
import com.santsg.tourvisio.dto.HotelSearchResponseItem;
import com.santsg.tourvisio.dto.AutocompleteRequest;
import com.santsg.tourvisio.dto.ArrivalAutocompleteResponse;
import com.santsg.tourvisio.service.HotelSearchService;
import com.santsg.tourvisio.service.ArrivalAutocompleteService;
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
@RequestMapping("/api/hotels")
@Tag(name = "Hotel Search Controller", description = "Endpoints for proxying TourVisio hotel searches")
public class HotelSearchController {

    private final HotelSearchService hotelSearchService;
    private final ArrivalAutocompleteService autocompleteService;

    public HotelSearchController(HotelSearchService hotelSearchService,
                                 ArrivalAutocompleteService autocompleteService) {
        this.hotelSearchService = hotelSearchService;
        this.autocompleteService = autocompleteService;
    }

    @PostMapping("/search")
    @Operation(summary = "Search hotels", description = "Forwards criteria to TourVisio API and returns matching hotels")
    public ResponseEntity<List<HotelSearchResponseItem>> searchHotels(@Valid @RequestBody HotelSearchRequest request) {
        List<HotelSearchResponseItem> results = hotelSearchService.searchHotels(request);
        return ResponseEntity.ok(results);
    }

    @PostMapping("/autocomplete")
    @Operation(summary = "Get autocomplete suggestions", description = "Forwards city or hotel query to TourVisio GetArrivalAutocomplete API")
    public ResponseEntity<List<ArrivalAutocompleteResponse>> autocomplete(@Valid @RequestBody AutocompleteRequest request) {
        List<ArrivalAutocompleteResponse> results = autocompleteService.getArrivalAutocomplete(request.getQuery());
        return ResponseEntity.ok(results);
    }

    @PostMapping("/checkindates")
    @Operation(summary = "Get hotel checkin dates", description = "Retrieves all available checkin dates for a hotel")
    public ResponseEntity<com.santsg.tourvisio.dto.tourvisio.GetCheckInDatesResponse> getCheckInDates(
            @Valid @RequestBody com.santsg.tourvisio.dto.tourvisio.GetCheckInDatesRequest request) {
        com.santsg.tourvisio.dto.tourvisio.GetCheckInDatesResponse response = hotelSearchService.getCheckInDates(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/productinfo")
    @Operation(summary = "Get hotel product info", description = "Retrieves hotel media and description info without prices")
    public ResponseEntity<com.santsg.tourvisio.dto.tourvisio.GetProductInfoResponse> getProductInfo(
            @Valid @RequestBody com.santsg.tourvisio.dto.tourvisio.GetProductInfoRequest request) {
        com.santsg.tourvisio.dto.tourvisio.GetProductInfoResponse response = hotelSearchService.getProductInfo(request);
        return ResponseEntity.ok(response);
    }
}
