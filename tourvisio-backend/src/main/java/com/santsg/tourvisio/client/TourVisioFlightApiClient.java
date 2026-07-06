package com.santsg.tourvisio.client;

import com.santsg.tourvisio.dto.FlightSearchRequest;
import com.santsg.tourvisio.dto.FlightSearchResponseItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class TourVisioFlightApiClient {

    @Value("${tourvisio.api.base-url}")
    private String baseUrl;

    @Value("${tourvisio.api.token}")
    private String token;

    @Value("${tourvisio.api.mock-mode}")
    private boolean mockMode;

    private final RestTemplate restTemplate = new RestTemplate();

    public List<FlightSearchResponseItem> searchFlights(FlightSearchRequest request) {
        if (mockMode) {
            log.info("TourVisio Flight search API running in MOCK mode.");
            return generateMockFlights(request);
        }

        try {
            String url = baseUrl + "/api/v2/flight/pricesearch";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + token);

            HttpEntity<FlightSearchRequest> entity = new HttpEntity<>(request, headers);

            log.info("Making TourVisio Flight Search request to: {}", url);
            ResponseEntity<FlightSearchResponseItem[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    FlightSearchResponseItem[].class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return List.of(response.getBody());
            } else {
                log.warn("TourVisio Flight API returned status code: {}", response.getStatusCode());
                return generateMockFlights(request);
            }
        } catch (Exception e) {
            log.error("Error connecting to TourVisio Flight API (falling back to mock data): {}", e.getMessage());
            return generateMockFlights(request);
        }
    }

    private List<FlightSearchResponseItem> generateMockFlights(FlightSearchRequest request) {
        List<FlightSearchResponseItem> flights = new ArrayList<>();
        String currency = request.getCurrency();
        int pax = request.getPassengerCount();

        flights.add(FlightSearchResponseItem.builder()
                .airline("Turkish Airlines")
                .departureTime(request.getDepartureDate().toString() + " 08:30")
                .arrivalTime(request.getDepartureDate().toString() + " 10:00")
                .transfers("Direct Flight")
                .baggage("20kg Checked + 8kg Cabin")
                .price(1850.0 * pax)
                .currency(currency)
                .build());

        flights.add(FlightSearchResponseItem.builder()
                .airline("Pegasus Airlines")
                .departureTime(request.getDepartureDate().toString() + " 14:15")
                .arrivalTime(request.getDepartureDate().toString() + " 15:45")
                .transfers("Direct Flight")
                .baggage("15kg Checked + 8kg Cabin")
                .price(1250.0 * pax)
                .currency(currency)
                .build());

        flights.add(FlightSearchResponseItem.builder()
                .airline("AJet")
                .departureTime(request.getDepartureDate().toString() + " 19:40")
                .arrivalTime(request.getDepartureDate().toString() + " 22:10")
                .transfers("1 Stop (ESB)")
                .baggage("20kg Checked")
                .price(1550.0 * pax)
                .currency(currency)
                .build());

        return flights;
    }
}
