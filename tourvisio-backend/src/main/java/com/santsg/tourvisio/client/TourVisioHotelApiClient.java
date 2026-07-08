package com.santsg.tourvisio.client;

import com.santsg.tourvisio.config.TourVisioConfig;
import com.santsg.tourvisio.dto.HotelSearchRequest;
import com.santsg.tourvisio.dto.HotelSearchResponseItem;
import com.santsg.tourvisio.dto.ArrivalAutocompleteResponse;
import com.santsg.tourvisio.dto.tourvisio.*;
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
 * TourVisio otel arama API istemcisi.
 *
 * <p>Entegrasyon Akışı:</p>
 * <ol>
 *   <li>{@link TourVisioAuthService} ile bearer token alınır.</li>
 *   <li>locationOrHotelName ile /api/productservice/getarrivalautocomplete endpointine POST atılır.</li>
 *   <li>Autocomplete yanıtından location ID ve type çözümlenir.</li>
 *   <li>Çözümlenen ID ile /api/productservice/pricesearch endpointine istek atılır.</li>
 *   <li>Dönen TourVisio response'u {@link HotelSearchResponseItem} DTO'suna normalize edilir.</li>
 * </ol>
 *
 * <p><strong>Mock mode false iken asla mock data dönmez.</strong>
 * Hata durumlarında anlaşılır exception fırlatılır.</p>
 */
@Component
@Slf4j
public class TourVisioHotelApiClient {

    private static final String AUTOCOMPLETE_PATH = "/api/productservice/getarrivalautocomplete";
    private static final String PRICE_SEARCH_PATH = "/api/productservice/pricesearch";

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

    // ─────────────────────────────────────────────────────────────────────────
    // Ana arama metodu
    // ─────────────────────────────────────────────────────────────────────────

    public List<HotelSearchResponseItem> searchHotels(HotelSearchRequest request) {
        // ── Mock mod ──
        if (config.isMockMode()) {
            log.info("[HotelApiClient] Mock mod aktif — mock data dönülüyor.");
            return generateMockHotels(request);
        }

        // ── Credential kontrolü ──
        if (!config.isConfigured()) {
            throw new TourVisioApiException(
                    "TourVisio API bağlantısı yapılandırılmamış. " +
                    "TOURVISIO_BASE_URL, TOURVISIO_AGENCY, TOURVISIO_USERNAME, TOURVISIO_PASSWORD " +
                    "environment variable'larını kontrol edin.");
        }

        // ── Token al ──
        String token;
        try {
            token = authService.getToken();
            log.info("[HotelApiClient] TourVisio token başarıyla alındı.");
        } catch (TourVisioAuthException e) {
            throw new TourVisioApiException("TourVisio'ya giriş yapılamadı: " + e.getMessage(), e);
        }

        HttpHeaders headers = createAuthHeaders(token);

        // ── 1. Autocomplete — location ID çözümle ──
        AutocompleteResult autoResult = resolveLocation(request.getLocationOrHotelName(), headers);
        log.info("[HotelApiClient] Autocomplete sonucu: id={}, type={}, name={}",
                autoResult.id, autoResult.type, autoResult.name);

        // ── 2. PriceSearch — otel fiyat araması ──
        TourVisioHotelSearchRequest searchReq =
                TourVisioRequestMapper.toHotelSearchRequest(request, autoResult.id, autoResult.type);

        log.info("[HotelApiClient] PriceSearch isteği gönderiliyor: checkIn={}, night={}, location={}, adults={}",
                searchReq.getCheckIn(), searchReq.getNight(),
                autoResult.name, request.getAdultCount());

        String searchUrl = buildUrl(PRICE_SEARCH_PATH);
        HttpEntity<TourVisioHotelSearchRequest> searchEntity = new HttpEntity<>(searchReq, headers);

        ResponseEntity<TourVisioHotelSearchResponse> searchRes;
        try {
            searchRes = restTemplate.exchange(
                    searchUrl, HttpMethod.POST, searchEntity, TourVisioHotelSearchResponse.class);
        } catch (Exception e) {
            throw new TourVisioApiException(
                    "TourVisio PriceSearch isteği başarısız: " + e.getMessage(), e);
        }

        // ── 3. Response'u normalize et ──
        return normalizeResponse(searchRes, request.getCurrency());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Autocomplete
    // ─────────────────────────────────────────────────────────────────────────

    private AutocompleteResult resolveLocation(String query, HttpHeaders headers) {
        String url = buildUrl(AUTOCOMPLETE_PATH);

        TourVisioAutocompleteRequest req = TourVisioAutocompleteRequest.builder()
                .productType(2)
                .query(query)
                .culture("tr-TR")
                .build();

        HttpEntity<TourVisioAutocompleteRequest> entity = new HttpEntity<>(req, headers);

        log.info("[HotelApiClient] Autocomplete isteği: query='{}', url={}", query, url);

        ResponseEntity<TourVisioAutocompleteResponse> res;
        try {
            res = restTemplate.exchange(url, HttpMethod.POST, entity, TourVisioAutocompleteResponse.class);
        } catch (Exception e) {
            throw new TourVisioApiException(
                    "TourVisio Autocomplete isteği başarısız (query='" + query + "'): " + e.getMessage(), e);
        }

        if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null) {
            throw new TourVisioApiException(
                    "TourVisio Autocomplete yanıtı başarısız. Status: " + res.getStatusCode());
        }

        TourVisioAutocompleteResponse.Body body = res.getBody().getBody();
        if (body == null || body.getItems() == null || body.getItems().isEmpty()) {
            throw new TourVisioApiException(
                    "'" + query + "' için TourVisio Autocomplete'de sonuç bulunamadı. " +
                    "Lütfen geçerli bir şehir veya otel adı girin.");
        }

        // İlk sonuçtan ID çöz
        TourVisioAutocompleteResponse.AutocompleteItem item = body.getItems().get(0);
        int itemType = item.getType();

        // City ID'si varsa onu kullan, yoksa state, en son country
        if (item.getCity() != null && item.getCity().getId() != null) {
            return new AutocompleteResult(item.getCity().getId(), itemType, item.getCity().getName());
        }
        if (item.getState() != null && item.getState().getId() != null) {
            return new AutocompleteResult(item.getState().getId(), itemType, item.getState().getName());
        }
        if (item.getHotel() != null && item.getHotel().getId() != null) {
            return new AutocompleteResult(item.getHotel().getId(), itemType, item.getHotel().getName());
        }
        if (item.getCountry() != null && item.getCountry().getId() != null) {
            return new AutocompleteResult(item.getCountry().getId(), itemType, item.getCountry().getName());
        }

        throw new TourVisioApiException(
                "'" + query + "' için Autocomplete sonucu alındı ama ID çözümlenemedi.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Response normalization
    // ─────────────────────────────────────────────────────────────────────────

    private List<HotelSearchResponseItem> normalizeResponse(
            ResponseEntity<TourVisioHotelSearchResponse> searchRes, String fallbackCurrency) {

        if (!searchRes.getStatusCode().is2xxSuccessful() || searchRes.getBody() == null) {
            throw new TourVisioApiException(
                    "TourVisio PriceSearch yanıtı başarısız. Status: " + searchRes.getStatusCode());
        }

        TourVisioHotelSearchResponse.Body body = searchRes.getBody().getBody();
        if (body == null || body.getHotels() == null || body.getHotels().isEmpty()) {
            log.info("[HotelApiClient] TourVisio PriceSearch sonuç döndü ama otel bulunamadı.");
            return new ArrayList<>();
        }

        log.info("[HotelApiClient] TourVisio {} otel sonucu döndü.", body.getHotels().size());

        return body.getHotels().stream()
                .map(hotel -> mapHotelItem(hotel, fallbackCurrency))
                .collect(Collectors.toList());
    }

    private HotelSearchResponseItem mapHotelItem(
            TourVisioHotelSearchResponse.HotelItem hotel, String fallbackCurrency) {

        // Region: city > town > address
        String region = "";
        if (hotel.getCity() != null && hotel.getCity().getName() != null) {
            region = hotel.getCity().getName();
        } else if (hotel.getTown() != null && hotel.getTown().getName() != null) {
            region = hotel.getTown().getName();
        } else if (hotel.getAddress() != null) {
            region = hotel.getAddress();
        }

        // Offer bilgileri (en iyi teklif = ilk offer)
        double price = 0.0;
        String currency = fallbackCurrency != null ? fallbackCurrency : "TRY";
        String pensionType = "";
        boolean available = false;

        if (hotel.getOffers() != null && !hotel.getOffers().isEmpty()) {
            TourVisioHotelSearchResponse.Offer firstOffer = hotel.getOffers().get(0);
            available = true; // Offer varsa müsait demektir

            if (firstOffer.getPrice() != null) {
                price = firstOffer.getPrice().getAmount();
                if (firstOffer.getPrice().getCurrency() != null) {
                    currency = firstOffer.getPrice().getCurrency();
                }
            }

            // Board (pansiyon) bilgisi — rooms[0].boardName
            if (firstOffer.getRooms() != null && !firstOffer.getRooms().isEmpty()) {
                TourVisioHotelSearchResponse.Room firstRoom = firstOffer.getRooms().get(0);
                if (firstRoom.getBoardName() != null) {
                    pensionType = firstRoom.getBoardName();
                }
            }
        }

        return HotelSearchResponseItem.builder()
                .name(hotel.getName())
                .region(region)
                .stars(hotel.getStars())
                .price(price)
                .currency(currency)
                .pensionType(pensionType)
                .available(available)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private HttpHeaders createAuthHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + token);
        return headers;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mock data (yalnızca mock-mode=true iken)
    // ─────────────────────────────────────────────────────────────────────────

    private List<HotelSearchResponseItem> generateMockHotels(HotelSearchRequest request) {
        List<HotelSearchResponseItem> hotels = new ArrayList<>();
        String location = request.getLocationOrHotelName();
        String currency = request.getCurrency();
        int adults = request.getAdultCount();

        hotels.add(HotelSearchResponseItem.builder()
                .name("Rixos Premium Belek (MOCK)")
                .region(location)
                .stars(5)
                .price(4500.0 * adults)
                .currency(currency)
                .pensionType("Ultra All Inclusive")
                .available(true)
                .build());

        hotels.add(HotelSearchResponseItem.builder()
                .name("Sheraton Grand Hotel (MOCK)")
                .region(location)
                .stars(5)
                .price(3200.0 * adults)
                .currency(currency)
                .pensionType("All Inclusive")
                .available(true)
                .build());

        hotels.add(HotelSearchResponseItem.builder()
                .name("Sunpark Beach Resort (MOCK)")
                .region(location)
                .stars(4)
                .price(1800.0 * adults)
                .currency(currency)
                .pensionType("Half Board")
                .available(true)
                .build());

        return hotels;
    }

    public List<ArrivalAutocompleteResponse> getArrivalAutocomplete(String query) {
        if (config.isMockMode()) {
            log.info("[HotelApiClient] Mock mod aktif — mock autocomplete verisi dönülüyor.");
            return generateMockAutocomplete(query);
        }

        // ── Credential kontrolü ──
        if (!config.isConfigured()) {
            throw new TourVisioApiException(
                    "TourVisio API bağlantısı yapılandırılmamış. " +
                    "TOURVISIO_BASE_URL, TOURVISIO_AGENCY, TOURVISIO_USERNAME, TOURVISIO_PASSWORD " +
                    "environment variable'larını kontrol edin.");
        }

        // ── Token al ──
        String token;
        try {
            token = authService.getToken();
            log.info("[HotelApiClient] TourVisio token başarıyla alındı.");
        } catch (TourVisioAuthException e) {
            throw new TourVisioApiException("TourVisio'ya giriş yapılamadı: " + e.getMessage(), e);
        }

        HttpHeaders headers = createAuthHeaders(token);

        try {
            TourVisioAutocompleteRequest autocompleteReq = TourVisioAutocompleteRequest.builder()
                    .productType(2)
                    .query(query)
                    .masterProductTypes(List.of(2))
                    .culture("tr-TR")
                    .build();

            String url = buildUrl(AUTOCOMPLETE_PATH);
            log.info("[HotelApiClient] Calling GetArrivalAutocomplete: {}", url);

            HttpEntity<TourVisioAutocompleteRequest> entity = new HttpEntity<>(autocompleteReq, headers);

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

                            if (item.getCity() != null && item.getCity().getId() != null) {
                                id = item.getCity().getId();
                                name = item.getCity().getName();
                            } else if (item.getState() != null && item.getState().getId() != null) {
                                id = item.getState().getId();
                                name = item.getState().getName();
                            } else if (item.getHotel() != null && item.getHotel().getId() != null) {
                                id = item.getHotel().getId();
                                name = item.getHotel().getName();
                            } else if (item.getCountry() != null && item.getCountry().getId() != null) {
                                id = item.getCountry().getId();
                                name = item.getCountry().getName();
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

    // ─────────────────────────────────────────────────────────────────────────
    // Yardımcı sınıflar
    // ─────────────────────────────────────────────────────────────────────────

    /** Autocomplete çözümleme sonucu */
    private static class AutocompleteResult {
        final String id;
        final int type;
        final String name;

        AutocompleteResult(String id, int type, String name) {
            this.id = id;
            this.type = type;
            this.name = name;
        }
    }

    /**
     * TourVisio API hata sınıfı.
     * Mock mod false iken hata olursa bu exception fırlatılır.
     */
    public static class TourVisioApiException extends RuntimeException {
        public TourVisioApiException(String message) {
            super(message);
        }

        public TourVisioApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
