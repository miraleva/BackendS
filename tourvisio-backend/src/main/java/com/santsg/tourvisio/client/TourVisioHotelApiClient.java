package com.santsg.tourvisio.client;

import com.santsg.tourvisio.config.TourVisioConfig;
import com.santsg.tourvisio.dto.HotelSearchRequest;
import com.santsg.tourvisio.dto.HotelSearchResponseItem;
import com.santsg.tourvisio.dto.ArrivalAutocompleteResponse;
import com.santsg.tourvisio.dto.tourvisio.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TourVisio otel arama API istemcisi.
 *
 * <p>Entegrasyon Akışı:</p>
 * <ol>
 *   <li>{@link TourVisioAuthService} ile bearer token alınır.</li>
 *   <li>Kullanıcının locationOrHotelName bilgisi /api/productservice/getarrivalautocomplete endpointine gönderilir.</li>
 *   <li>Autocomplete yanıtı içinden şehir/otel ID'si çözümlenir.</li>
 *   <li>Çözümlenen ID ile /api/productservice/pricesearch endpointine istek atılır.</li>
 *   <li>Dönen TourVisio response'u {@link HotelSearchResponseItem} DTO'suna normalize edilir.</li>
 * </ol>
 */
@Component
@Slf4j
public class TourVisioHotelApiClient {

    private static final String AUTOCOMPLETE_PATH = "/api/productservice/getarrivalautocomplete";
    private static final String HOTEL_SEARCH_PATH = "/api/productservice/pricesearch";

    private final TourVisioConfig config;
    private final TourVisioAuthService authService;
    private final RestTemplate restTemplate;

    public TourVisioHotelApiClient(TourVisioConfig config,
                                   TourVisioAuthService authService,
                                   @Qualifier("tourVisioRestTemplate") RestTemplate restTemplate) {
        this.config = config;
        this.authService = authService;
        this.restTemplate = restTemplate;
    }

    public List<HotelSearchResponseItem> searchHotels(HotelSearchRequest request) {
        if (config.isMockMode() || !config.isConfigured()) {
            log.info("[HotelApiClient] Mock mod aktif veya credential eksik — mock data dönülüyor.");
            return generateMockHotels(request);
        }

        try {
            String token = authService.getToken();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + token);

            // 1. GetArrivalAutocomplete
            String autocompleteUrl = config.getBaseUrl() + AUTOCOMPLETE_PATH;
            TourVisioAutocompleteRequest autocompleteReq = TourVisioRequestMapper.toAutocompleteRequest(request);
            HttpEntity<TourVisioAutocompleteRequest> autocompleteEntity = new HttpEntity<>(autocompleteReq, headers);

            log.info("[HotelApiClient] Autocomplete isteği gönderiliyor: {}", autocompleteUrl);
            ResponseEntity<TourVisioAutocompleteResponse> autocompleteRes = restTemplate.exchange(
                    autocompleteUrl,
                    HttpMethod.POST,
                    autocompleteEntity,
                    TourVisioAutocompleteResponse.class
            );

            String resolvedId = null;
            if (autocompleteRes.getStatusCode().is2xxSuccessful() && autocompleteRes.getBody() != null
                    && autocompleteRes.getBody().getBody() != null
                    && autocompleteRes.getBody().getBody().getItems() != null
                    && !autocompleteRes.getBody().getBody().getItems().isEmpty()) {

                // Get first matched city or hotel ID
                TourVisioAutocompleteResponse.AutocompleteItem firstItem = autocompleteRes.getBody().getBody().getItems().get(0);
                if (firstItem.getCity() != null) {
                    resolvedId = firstItem.getCity().getId();
                    log.info("[HotelApiClient] Bulunan City ID: {}", resolvedId);
                } else if (firstItem.getHotel() != null) {
                    resolvedId = firstItem.getHotel().getId();
                    log.info("[HotelApiClient] Bulunan Hotel ID: {}", resolvedId);
                }
            }

            // 2. PriceSearch
            String searchUrl = config.getBaseUrl() + HOTEL_SEARCH_PATH;
            TourVisioHotelSearchRequest searchReq = TourVisioRequestMapper.toHotelSearchRequest(request, resolvedId);
            HttpEntity<TourVisioHotelSearchRequest> searchEntity = new HttpEntity<>(searchReq, headers);

            log.info("[HotelApiClient] Otel arama isteği gönderiliyor (resolvedId={}): {}", searchUrl, resolvedId);
            ResponseEntity<TourVisioHotelSearchResponse> searchRes = restTemplate.exchange(
                    searchUrl,
                    HttpMethod.POST,
                    searchEntity,
                    TourVisioHotelSearchResponse.class
            );

            if (searchRes.getStatusCode().is2xxSuccessful() && searchRes.getBody() != null
                    && searchRes.getBody().getHotels() != null) {
                return searchRes.getBody().getHotels().stream()
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
                log.warn("[HotelApiClient] Otel arama başarısız (status={}) — mock veriye düşülüyor.", searchRes.getStatusCode());
                return generateMockHotels(request);
            }

        } catch (TourVisioAuthService.TourVisioAuthException e) {
            log.error("[HotelApiClient] TourVisio auth hatası — mock veriye düşülüyor: {}", e.getMessage());
            return generateMockHotels(request);
        } catch (Exception e) {
            log.error("[HotelApiClient] TourVisio API entegrasyon hatası — mock veriye düşülüyor: {}", e.getMessage());
            return generateMockHotels(request);
        }
    }

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

    public List<ArrivalAutocompleteResponse> getArrivalAutocomplete(String query) {
        if (config.isMockMode() || !config.isConfigured()) {
            log.info("[HotelApiClient] Mock mod aktif veya credential eksik — mock autocomplete verisi dönülüyor.");
            return generateMockAutocomplete(query);
        }

        try {
            TourVisioAutocompleteRequest autocompleteReq = TourVisioAutocompleteRequest.builder()
                    .productType(2)
                    .query(query)
                    .masterProductTypes(List.of(2))
                    .culture("tr-TR")
                    .build();

            String url = buildUrl(AUTOCOMPLETE_PATH);
            log.info("[HotelApiClient] Calling GetArrivalAutocomplete: {}", url);

            HttpEntity<TourVisioAutocompleteRequest> entity = new HttpEntity<>(autocompleteReq);

            ResponseEntity<TourVisioAutocompleteResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    TourVisioAutocompleteResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null
                    && response.getBody().getBody() != null
                    && response.getBody().getBody().getItems() != null) {

                return response.getBody().getBody().getItems().stream()
                        .map(item -> {
                            String id = null;
                            String name = null;

                            if (item.getHotel() != null) {
                                id = item.getHotel().getId();
                                name = item.getHotel().getName();
                            } else if (item.getCity() != null) {
                                id = item.getCity().getId();
                                name = item.getCity().getName();
                            }

                            String countryName = item.getCountry() != null ? item.getCountry().getName() : null;
                            String cityName = item.getCity() != null ? item.getCity().getName() : null;

                            return ArrivalAutocompleteResponse.builder()
                                    .id(id)
                                    .name(name)
                                    .type(item.getType())
                                    .country(countryName)
                                    .city(cityName)
                                    .build();
                        })
                        .collect(Collectors.toList());
            } else {
                log.warn("[HotelApiClient] Autocomplete API error (status={}) — falling back to mock autocomplete.", response.getStatusCode());
                return generateMockAutocomplete(query);
            }
        } catch (Exception e) {
            log.error("[HotelApiClient] Autocomplete integration exception — falling back to mock: {}", e.getMessage(), e);
            return generateMockAutocomplete(query);
        }
    }

    private List<ArrivalAutocompleteResponse> generateMockAutocomplete(String query) {
        List<ArrivalAutocompleteResponse> results = new ArrayList<>();
        String queryLower = query != null ? query.toLowerCase() : "";

        if (queryLower.contains("ant") || queryLower.contains("aly")) {
            results.add(ArrivalAutocompleteResponse.builder()
                    .id("23494")
                    .name("Antalya")
                    .type(1)
                    .country("Turkey")
                    .city("Antalya")
                    .build());
            results.add(ArrivalAutocompleteResponse.builder()
                    .id("1269469")
                    .name("Rixos Premium Belek")
                    .type(2)
                    .country("Turkey")
                    .city("Antalya")
                    .build());
        } else if (queryLower.contains("ist") || queryLower.contains("tan")) {
            results.add(ArrivalAutocompleteResponse.builder()
                    .id("IST")
                    .name("Istanbul")
                    .type(1)
                    .country("Turkey")
                    .city("Istanbul")
                    .build());
        } else {
            results.add(ArrivalAutocompleteResponse.builder()
                    .id("1000")
                    .name(query)
                    .type(1)
                    .country("Turkey")
                    .city(query)
                    .build());
        }
        return results;
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
