package com.santsg.tourvisio.client;

import com.santsg.tourvisio.dto.FlightSearchRequest;
import com.santsg.tourvisio.dto.FlightSearchResponseItem;
import com.santsg.tourvisio.dto.tourvisio.TourVisioFlightSearchRequest;
import com.santsg.tourvisio.dto.tourvisio.TourVisioFlightSearchResponse;
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
public class TourVisioFlightApiClient {

    @Value("${tourvisio.api.base-url}")
    private String baseUrl;

    @Value("${tourvisio.api.token}")
    private String token;

    @Value("${tourvisio.api.mock-mode}")
    private boolean mockMode;

    private final RestTemplate restTemplate = new RestTemplate();

    public List<FlightSearchResponseItem> searchFlights(FlightSearchRequest request) {
        if (mockMode || baseUrl == null || baseUrl.trim().isEmpty() || token == null || token.trim().isEmpty()) {
            log.info("TourVisio Flight search API running in MOCK mode (or missing connection credentials).");
            return generateMockFlights(request);
        }

        try {
            String url = baseUrl + "/api/v2/flight/pricesearch";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + token);

            TourVisioFlightSearchRequest tvRequest = TourVisioFlightSearchRequest.builder()
                    .departureLocation(request.getDepartureLocation())
                    .arrivalLocation(request.getArrivalLocation())
                    .departureDate(request.getDepartureDate().toString())
                    .passengerCount(request.getPassengerCount())
                    .tripType(request.getTripType())
                    .currency(request.getCurrency())
                    .build();

            HttpEntity<TourVisioFlightSearchRequest> entity = new HttpEntity<>(tvRequest, headers);

            log.info("Making TourVisio Flight Search request to: {}", url);
            ResponseEntity<TourVisioFlightSearchResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    TourVisioFlightSearchResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && response.getBody().getFlights() != null) {
                return response.getBody().getFlights().stream()
                        .map(tvItem -> FlightSearchResponseItem.builder()
                                .airline(tvItem.getAirline())
                                .departureTime(tvItem.getDepartureTime())
                                .arrivalTime(tvItem.getArrivalTime())
                                .transfers(tvItem.getTransfers())
                                .baggage(tvItem.getBaggage())
                                .price(tvItem.getPrice())
                                .currency(tvItem.getCurrency())
                                .build())
                        .collect(Collectors.toList());
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
