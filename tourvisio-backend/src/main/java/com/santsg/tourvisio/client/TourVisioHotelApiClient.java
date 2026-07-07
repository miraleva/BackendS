package com.santsg.tourvisio.client;

import com.santsg.tourvisio.dto.HotelSearchRequest;
import com.santsg.tourvisio.dto.HotelSearchResponseItem;
import com.santsg.tourvisio.dto.tourvisio.TourVisioHotelSearchRequest;
import com.santsg.tourvisio.dto.tourvisio.TourVisioHotelSearchResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class TourVisioHotelApiClient {

    @Value("${tourvisio.api.base-url}")
    private String baseUrl;

    @Value("${tourvisio.api.token}")
    private String token;

    @Value("${tourvisio.api.mock-mode:true}")
    private boolean mockMode;

    private final RestTemplate restTemplate = new RestTemplate();

    public List<HotelSearchResponseItem> searchHotels(HotelSearchRequest request) {
        if (mockMode || baseUrl == null || baseUrl.trim().isEmpty() || token == null || token.trim().isEmpty()) {
            log.info("TourVisio Hotel search API running in MOCK mode (or missing connection credentials).");
            return generateMockHotels(request);
        }

        try {
            String url = baseUrl + "/api/v2/hotel/pricesearch";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + token);

            TourVisioHotelSearchRequest tvRequest = TourVisioHotelSearchRequest.builder()
                    .checkInDate(request.getCheckInDate().toString())
                    .checkOutDate(request.getCheckOutDate().toString())
                    .destination(request.getLocation())
                    .nationality("TR")
                    .adultCount(request.getAdultsCount())
                    .currency(request.getCurrency())
                    .build();

            HttpEntity<TourVisioHotelSearchRequest> entity = new HttpEntity<>(tvRequest, headers);

            log.info("Making TourVisio Hotel Search request to: {}", url);
            ResponseEntity<TourVisioHotelSearchResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    TourVisioHotelSearchResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && response.getBody().getHotels() != null) {
                return response.getBody().getHotels().stream()
                        .map(tvItem -> HotelSearchResponseItem.builder()
                                .name(tvItem.getName())
                                .region(tvItem.getRegion())
                                .stars(tvItem.getStars())
                                .price(tvItem.getPrice())
                                .currency(tvItem.getCurrency())
                                .pensionType(tvItem.getPensionType())
                                .available(tvItem.isAvailable())
                                .build())
                        .collect(Collectors.toList());
            } else {
                log.warn("TourVisio Hotel API returned status code: {}", response.getStatusCode());
                return generateMockHotels(request);
            }
        } catch (Exception e) {
            log.error("Error connecting to TourVisio Hotel API (falling back to mock data): {}", e.getMessage());
            return generateMockHotels(request);
        }
    }

    private List<HotelSearchResponseItem> generateMockHotels(HotelSearchRequest request) {
        List<HotelSearchResponseItem> hotels = new ArrayList<>();
        String location = request.getLocation();
        String currency = request.getCurrency();
        int adults = request.getAdultsCount();

        String locationLower = location != null ? location.toLowerCase() : "";

        if (locationLower.contains("antalya") || locationLower.contains("alanya") || 
            locationLower.contains("bodrum") || locationLower.contains("marmaris") || 
            locationLower.contains("fethiye") || locationLower.contains("izmir")) {
            
            hotels.add(HotelSearchResponseItem.builder()
                    .name("Rixos Premium Belek")
                    .region(location)
                    .stars(5)
                    .price(4500.0 * adults)
                    .currency(currency)
                    .pensionType("Ultra All Inclusive")
                    .available(true)
                    .build());
            
            hotels.add(HotelSearchResponseItem.builder()
                    .name("Sheraton Grand Hotel")
                    .region(location)
                    .stars(5)
                    .price(3200.0 * adults)
                    .currency(currency)
                    .pensionType("All Inclusive")
                    .available(true)
                    .build());
            
            hotels.add(HotelSearchResponseItem.builder()
                    .name("Sunpark Beach Resort")
                    .region(location)
                    .stars(4)
                    .price(1800.0 * adults)
                    .currency(currency)
                    .pensionType("Half Board")
                    .available(true)
                    .build());
        } else {
            hotels.add(HotelSearchResponseItem.builder()
                    .name("Grand Central Hotel")
                    .region(location)
                    .stars(4)
                    .price(2500.0 * adults)
                    .currency(currency)
                    .pensionType("Bed & Breakfast")
                    .available(true)
                    .build());
            
            hotels.add(HotelSearchResponseItem.builder()
                    .name("Plaza Boutique Hotel")
                    .region(location)
                    .stars(5)
                    .price(3800.0 * adults)
                    .currency(currency)
                    .pensionType("Bed & Breakfast")
                    .available(true)
                    .build());
            
            hotels.add(HotelSearchResponseItem.builder()
                    .name("Cozy Star Pension")
                    .region(location)
                    .stars(3)
                    .price(950.0 * adults)
                    .currency(currency)
                    .pensionType("Room Only")
                    .available(false)
                    .build());
        }
        return hotels;
    }
}
