package com.santsg.tourvisio.client;

import com.santsg.tourvisio.config.TourVisioConfig;
import com.santsg.tourvisio.dto.HotelSearchRequest;
import com.santsg.tourvisio.dto.HotelSearchResponseItem;
import com.santsg.tourvisio.dto.tourvisio.TourVisioHotelSearchRequest;
import com.santsg.tourvisio.dto.tourvisio.TourVisioHotelSearchResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TourVisio otel arama API istemcisi.
 *
 * <p>Gerçek mod ({@code mockMode=false}) aktifken {@link TourVisioAuthService}
 * üzerinden token alır ve TourVisio endpointine istek atar.
 * Credential'lar eksikse veya mock mod açıksa sahte veri döner.</p>
 *
 * <p><strong>TODO:</strong> Gerçek otel arama endpoint path'i doküman gelince
 * {@code HOTEL_SEARCH_PATH} sabitinde güncellenmelidir.</p>
 */
@Component
@Slf4j
public class TourVisioHotelApiClient {

    /**
     * TourVisio otel arama endpoint path'i.
     * TODO: Doküman gelince doğru path buraya yazılacak.
     *       Örnek: "/api/productservice/pricesearch"
     */
    private static final String HOTEL_SEARCH_PATH = "/api/productservice/pricesearch";

    private final TourVisioConfig config;
    private final TourVisioAuthService authService;
    private final RestTemplate restTemplate;

    public TourVisioHotelApiClient(TourVisioConfig config,
                                   TourVisioAuthService authService) {
        this.config = config;
        this.authService = authService;
        this.restTemplate = new RestTemplate();
    }

    public List<HotelSearchResponseItem> searchHotels(HotelSearchRequest request) {
        if (config.isMockMode() || !config.isConfigured()) {
            log.info("[HotelApiClient] Mock mod aktif veya credential eksik — mock data dönülüyor.");
            return generateMockHotels(request);
        }

        try {
            String url = config.getBaseUrl() + HOTEL_SEARCH_PATH;
            String token = authService.getToken();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + token);

            TourVisioHotelSearchRequest.LocationCriteria loc = TourVisioHotelSearchRequest.LocationCriteria.builder()
                    .id("23494") // standard ID or dynamically resolved if mapping existed
                    .type(2)
                    .build();

            TourVisioHotelSearchRequest.RoomCriteria room = TourVisioHotelSearchRequest.RoomCriteria.builder()
                    .adult(request.getAdultCount())
                    .childAges(new ArrayList<>())
                    .build();

            TourVisioHotelSearchRequest tvRequest = TourVisioHotelSearchRequest.builder()
                    .productType(2)
                    .checkAllotment(true)
                    .checkStopSale(true)
                    .getOnlyDiscountedPrice(false)
                    .getOnlyBestOffers(true)
                    .arrivalLocations(List.of(loc))
                    .roomCriteria(List.of(room))
                    .checkIn(request.getCheckInDate().toString() + "T00:00:00")
                    .checkOut(request.getCheckOutDate().toString() + "T00:00:00")
                    .currency(request.getCurrency())
                    .culture("tr-TR")
                    .nationality("TR")
                    .build();

            HttpEntity<TourVisioHotelSearchRequest> entity = new HttpEntity<>(tvRequest, headers);

            log.info("[HotelApiClient] TourVisio otel arama isteği: {}", url);
            ResponseEntity<TourVisioHotelSearchResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    TourVisioHotelSearchResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null
                    && response.getBody().getHotels() != null) {
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
                log.warn("[HotelApiClient] TourVisio API status: {} — mock'a düşülüyor.",
                        response.getStatusCode());
                return generateMockHotels(request);
            }
        } catch (TourVisioAuthService.TourVisioAuthException e) {
            log.error("[HotelApiClient] TourVisio auth hatası: {} — mock'a düşülüyor.", e.getMessage());
            return generateMockHotels(request);
        } catch (Exception e) {
            log.error("[HotelApiClient] TourVisio API hatası: {} — mock'a düşülüyor.", e.getMessage());
            return generateMockHotels(request);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mock data
    // ─────────────────────────────────────────────────────────────────────────

    private List<HotelSearchResponseItem> generateMockHotels(HotelSearchRequest request) {
        List<HotelSearchResponseItem> hotels = new ArrayList<>();
        String location = request.getLocationOrHotelName();
        String currency = request.getCurrency();
        int adults = request.getAdultCount();

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
