package com.santsg.tourvisio.controller;

import com.santsg.tourvisio.dto.HotelSearchRequest;
import com.santsg.tourvisio.dto.HotelSearchResponseItem;
import com.santsg.tourvisio.service.HotelSearchService;
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

    public HotelSearchController(HotelSearchService hotelSearchService) {
        this.hotelSearchService = hotelSearchService;
    }

    @PostMapping("/search")
    @Operation(summary = "Search hotels", description = "Forwards criteria to TourVisio API and returns matching hotels")
    public ResponseEntity<List<HotelSearchResponseItem>> searchHotels(@Valid @RequestBody HotelSearchRequest request) {
        List<HotelSearchResponseItem> results = hotelSearchService.searchHotels(request);
        return ResponseEntity.ok(results);
    }
}
