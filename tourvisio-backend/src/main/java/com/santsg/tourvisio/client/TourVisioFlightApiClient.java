package com.santsg.tourvisio.client;
 
import com.santsg.tourvisio.config.TourVisioConfig;
import com.santsg.tourvisio.dto.FlightSearchRequest;
import com.santsg.tourvisio.dto.FlightSearchResponseItem;
import com.santsg.tourvisio.dto.ArrivalAutocompleteResponse;
import com.santsg.tourvisio.service.ArrivalAutocompleteService;
import com.santsg.tourvisio.dto.tourvisio.TourVisioFlightSearchRequest;
import com.santsg.tourvisio.dto.tourvisio.TourVisioFlightSearchResponse;
import com.santsg.tourvisio.exception.TourVisioAuthException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
 
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TourVisio uçuş arama API istemcisi.
 *
 * <p>Gerçek mod ({@code mockMode=false}) aktifken {@link TourVisioAuthService}
 * üzerinden token alır ve TourVisio endpointine istek atar.
 * Credential'lar eksikse veya mock mod açıksa sahte veri döner.</p>
 *
 * <p><strong>TODO:</strong> Gerçek uçuş arama endpoint path'i doküman gelince
 * {@code FLIGHT_SEARCH_PATH} sabitinde güncellenmelidir.</p>
 */
@Component
@Slf4j
public class TourVisioFlightApiClient {

    /**
     * TourVisio uçuş arama endpoint path'i.
     * TODO: Doküman gelince doğru path buraya yazılacak.
     *       Örnek: "/api/flightservice/search"
     */
    private static final String FLIGHT_SEARCH_PATH = "/api/flightservice/search";

    private final TourVisioConfig config;
    private final TourVisioAuthService authService;
    private final RestTemplate restTemplate;
    private final ArrivalAutocompleteService autocompleteService;
 
    public TourVisioFlightApiClient(TourVisioConfig config,
                                    TourVisioAuthService authService,
                                    @Qualifier("tourVisioRestTemplate") RestTemplate restTemplate,
                                    ArrivalAutocompleteService autocompleteService) {
        this.config = config;
        this.authService = authService;
        this.restTemplate = restTemplate;
        this.autocompleteService = autocompleteService;
    }
 
    private TourVisioFlightSearchRequest.LocationCriteria resolveLocation(String locationName) {
        if (locationName == null || locationName.isBlank()) {
            throw new IllegalArgumentException("Location not recognized: " + locationName);
        }
        List<ArrivalAutocompleteResponse> results = autocompleteService.getArrivalAutocomplete(locationName);
        if (results == null || results.isEmpty()) {
            throw new IllegalArgumentException("Location not recognized: " + locationName);
        }
        ArrivalAutocompleteResponse first = results.get(0);
        if (first.getId() == null || first.getId().isBlank()) {
            throw new IllegalArgumentException("Location not recognized: " + locationName);
        }
        return TourVisioFlightSearchRequest.LocationCriteria.builder()
                .id(first.getId())
                .type(first.getType() != null ? first.getType() : 2)
                .build();
    }
 
    public List<FlightSearchResponseItem> searchFlights(FlightSearchRequest request) {
        if (config.isMockMode() || !config.isConfigured()) {
            log.info("[FlightApiClient] Mock mod aktif veya credential eksik — mock data dönülüyor.");
            return generateMockFlights(request);
        }
 
        try {
            String url = buildUrl(FLIGHT_SEARCH_PATH);
            String token = authService.getToken();
 
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + token);
 
            TourVisioFlightSearchRequest.LocationCriteria dep = resolveLocation(request.getDepartureLocation());
            TourVisioFlightSearchRequest.LocationCriteria arr = resolveLocation(request.getArrivalLocation());

            TourVisioFlightSearchRequest.RoomCriteria room = TourVisioFlightSearchRequest.RoomCriteria.builder()
                    .adult(request.getPassengerCount())
                    .childAges(new ArrayList<>())
                    .build();

            TourVisioFlightSearchRequest tvRequest = TourVisioFlightSearchRequest.builder()
                    .productType(13)
                    .departureLocations(List.of(dep))
                    .arrivalLocations(List.of(arr))
                    .checkIn(request.getDepartureDate().toString())
                    .night(7)
                    .currency(request.getCurrency())
                    .culture("tr-TR")
                    .nationality("TR")
                    .roomCriteria(List.of(room))
                    .build();

            HttpEntity<TourVisioFlightSearchRequest> entity = new HttpEntity<>(tvRequest, headers);

            log.info("[FlightApiClient] TourVisio uçuş arama isteği: {}", url);
            ResponseEntity<TourVisioFlightSearchResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    TourVisioFlightSearchResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null
                    && response.getBody().getFlights() != null) {
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
                log.warn("[FlightApiClient] TourVisio API status: {} — mock'a düşülüyor.",
                        response.getStatusCode());
                return generateMockFlights(request);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (TourVisioAuthException e) {
            log.error("[FlightApiClient] TourVisio auth hatası: {} — mock'a düşülüyor.", e.getMessage());
            return generateMockFlights(request);
        } catch (Exception e) {
            log.error("[FlightApiClient] TourVisio API hatası: {} — mock'a düşülüyor.", e.getMessage());
            return generateMockFlights(request);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mock data
    // ─────────────────────────────────────────────────────────────────────────

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

    private String buildUrl(String path) {
        String baseUrl = config.getBaseUrl();
        if (baseUrl == null) {
            baseUrl = "";
        }
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (baseUrl.endsWith("/api/") && path.startsWith("api/")) {
            path = path.substring(4);
        }
        return baseUrl + path;
    }
}
