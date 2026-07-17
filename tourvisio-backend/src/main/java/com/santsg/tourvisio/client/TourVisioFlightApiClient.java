package com.santsg.tourvisio.client;

import com.santsg.tourvisio.config.TourVisioConfig;
import com.santsg.tourvisio.dto.FlightSearchRequest;
import com.santsg.tourvisio.dto.FlightSearchResponseItem;
import com.santsg.tourvisio.dto.tourvisio.TourVisioAutocompleteResponse;
import com.santsg.tourvisio.dto.tourvisio.TourVisioFlightAutocompleteRequest;
import com.santsg.tourvisio.dto.tourvisio.TourVisioFlightSearchRequest;
import com.santsg.tourvisio.dto.tourvisio.TourVisioFlightSearchResponse;
import com.santsg.tourvisio.exception.TourVisioApiException;
import com.santsg.tourvisio.exception.TourVisioAuthException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.temporal.ChronoUnit;
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
 * <p>Endpoint'ler ve istek/cevap formatları docs-ai.santsg.com/tourvisio
 * &gt; "Flight Product" dokümantasyonundan doğrulanmıştır. Önemli:
 * uçuş için ürün tipi ({@code productType}) 3'tür (13 değil — 13 "Dynamic"
 * paket tipidir ve önceki kod bunu yanlışlıkla kullanıyordu). Uçuş
 * aramaları da otel aramasıyla aynı {@code /api/productservice/pricesearch}
 * endpoint'ini kullanır; ayrı bir "/api/flightservice/search" endpoint'i
 * yoktur.</p>
 */
@Component
@Slf4j
public class TourVisioFlightApiClient {

    private static final int FLIGHT_PRODUCT_TYPE = 3; // Product Types tablosu: 3 = Flight

    // Autocomplete Response Types (docs-ai.santsg.com > Enumerations)
    private static final int CITY_AUTOCOMPLETE_TYPE = 1;
    private static final int AIRPORT_AUTOCOMPLETE_TYPE = 3;

    // Location Types — "Mapping For Autocomplete & Location Types" tablosuna gore:
    // Autocomplete City(1) -> Location City(2), Autocomplete Airport(3) -> Location Airport(5)
    private static final int CITY_LOCATION_TYPE = 2;
    private static final int AIRPORT_LOCATION_TYPE = 5;

    private static final String DEPARTURE_AUTOCOMPLETE_PATH = "/api/productservice/getdepartureautocomplete";
    private static final String ARRIVAL_AUTOCOMPLETE_PATH = "/api/productservice/getarrivalautocomplete";
    private static final String PRICE_SEARCH_PATH = "/api/productservice/pricesearch";

    private final TourVisioConfig config;
    private final TourVisioAuthService authService;
    private final RestTemplate restTemplate;

    public TourVisioFlightApiClient(TourVisioConfig config,
                                    TourVisioAuthService authService,
                                    @Qualifier("tourVisioRestTemplate") RestTemplate restTemplate) {
        this.config = config;
        this.authService = authService;
        this.restTemplate = restTemplate;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Konum çözümleme (Departure / Arrival Autocomplete)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * TourVisio'nun autocomplete'i bazen bulanik metin eslesmesi yuzunden
     * (ornek: "Munich" sorgusuna alakasiz "Municipal" isimli kucuk ABD
     * havalimanlarini) hatali "Airport" (type=3) sonuclari on sirada
     * dondurebiliyor. "City" (type=1, "tum havaalanlari") sonucu ise her
     * zaman dogru sehri temsil ediyor ve genelde ilk sirada geliyor; bu
     * yuzden varsa once onu tercih ediyoruz.
     */
    private TourVisioAutocompleteResponse.AutocompleteItem pickBestLocationItem(
            List<TourVisioAutocompleteResponse.AutocompleteItem> items, String locationName) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Location not recognized: " + locationName);
        }
        return items.stream()
                .filter(item -> item.getType() == CITY_AUTOCOMPLETE_TYPE && item.getCity() != null)
                .findFirst()
                .or(() -> items.stream()
                        .filter(item -> item.getType() == AIRPORT_AUTOCOMPLETE_TYPE && item.getAirport() != null)
                        .findFirst())
                .orElse(items.get(0));
    }

    private TourVisioFlightSearchRequest.LocationCriteria toLocationCriteria(
            TourVisioAutocompleteResponse.AutocompleteItem item, String locationName) {
        String id;
        int locationType;
        if (item.getType() == CITY_AUTOCOMPLETE_TYPE && item.getCity() != null) {
            id = item.getCity().getId();
            locationType = CITY_LOCATION_TYPE;
        } else if (item.getAirport() != null) {
            id = item.getAirport().getId();
            locationType = AIRPORT_LOCATION_TYPE;
        } else if (item.getCity() != null) {
            id = item.getCity().getId();
            locationType = CITY_LOCATION_TYPE;
        } else {
            id = null;
            locationType = AIRPORT_LOCATION_TYPE;
        }
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Location not recognized: " + locationName);
        }
        return TourVisioFlightSearchRequest.LocationCriteria.builder()
                .id(id)
                .type(locationType)
                .build();
    }

    private TourVisioFlightSearchRequest.LocationCriteria resolveDepartureLocation(
            String locationName, HttpHeaders headers) {
        if (locationName == null || locationName.isBlank()) {
            throw new IllegalArgumentException("Location not recognized: " + locationName);
        }

        TourVisioFlightAutocompleteRequest req = TourVisioFlightAutocompleteRequest.builder()
                .productType(FLIGHT_PRODUCT_TYPE)
                .query(locationName)
                .serviceType("1")
                .culture("tr-TR")
                .build();

        String url = buildUrl(DEPARTURE_AUTOCOMPLETE_PATH);
        HttpEntity<TourVisioFlightAutocompleteRequest> entity = new HttpEntity<>(req, headers);
        ResponseEntity<TourVisioAutocompleteResponse> response =
                restTemplate.exchange(url, HttpMethod.POST, entity, TourVisioAutocompleteResponse.class);

        if (response.getBody() == null || response.getBody().getBody() == null) {
            throw new TourVisioApiException("TourVisio Departure Autocomplete cevabı boş: " + locationName);
        }

        TourVisioAutocompleteResponse.AutocompleteItem best =
                pickBestLocationItem(response.getBody().getBody().getItems(), locationName);
        return toLocationCriteria(best, locationName);
    }

    private TourVisioFlightSearchRequest.LocationCriteria resolveArrivalLocation(
            String locationName, TourVisioFlightSearchRequest.LocationCriteria departure, HttpHeaders headers) {
        if (locationName == null || locationName.isBlank()) {
            throw new IllegalArgumentException("Location not recognized: " + locationName);
        }

        TourVisioFlightAutocompleteRequest req = TourVisioFlightAutocompleteRequest.builder()
                .productType(FLIGHT_PRODUCT_TYPE)
                .query(locationName)
                .serviceType("1")
                .departureLocations(List.of(departure))
                .culture("tr-TR")
                .build();

        String url = buildUrl(ARRIVAL_AUTOCOMPLETE_PATH);
        HttpEntity<TourVisioFlightAutocompleteRequest> entity = new HttpEntity<>(req, headers);
        ResponseEntity<TourVisioAutocompleteResponse> response =
                restTemplate.exchange(url, HttpMethod.POST, entity, TourVisioAutocompleteResponse.class);

        if (response.getBody() == null || response.getBody().getBody() == null) {
            throw new TourVisioApiException("TourVisio Arrival Autocomplete cevabı boş: " + locationName);
        }

        TourVisioAutocompleteResponse.AutocompleteItem best =
                pickBestLocationItem(response.getBody().getBody().getItems(), locationName);
        return toLocationCriteria(best, locationName);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Ana arama metodu
    // ─────────────────────────────────────────────────────────────────────────

    public List<FlightSearchResponseItem> searchFlights(FlightSearchRequest request) {
        if (config.isMockMode()) {
            log.info("[FlightApiClient] Mock mod aktif — mock data dönülüyor.");
            return generateMockFlights(request);
        }

        if (!config.isConfigured()) {
            throw new TourVisioApiException("TourVisio API bağlantısı yapılandırılmamış.");
        }

        try {
            String token = authService.getToken();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + token);

            TourVisioFlightSearchRequest.LocationCriteria dep =
                    resolveDepartureLocation(request.getDepartureLocation(), headers);
            TourVisioFlightSearchRequest.LocationCriteria arr =
                    resolveArrivalLocation(request.getArrivalLocation(), dep, headers);

            boolean roundTrip = "ROUND_TRIP".equalsIgnoreCase(request.getTripType()) && request.getReturnDate() != null;
            int nights = roundTrip
                    ? (int) ChronoUnit.DAYS.between(request.getDepartureDate(), request.getReturnDate())
                    : 1;
            if (nights <= 0) {
                nights = 1;
            }

            TourVisioFlightSearchRequest.PassengerCriteria adults =
                    TourVisioFlightSearchRequest.PassengerCriteria.builder()
                            .type(1) // Passenger Types: 1 = Adult
                            .count(request.getPassengerCount())
                            .build();

            TourVisioFlightSearchRequest tvRequest = TourVisioFlightSearchRequest.builder()
                    .productType(FLIGHT_PRODUCT_TYPE)
                    .serviceTypes(List.of(roundTrip ? "2" : "1"))
                    .checkIn(request.getDepartureDate().toString())
                    .checkOut(roundTrip ? request.getReturnDate().toString() : null)
                    .night(nights)
                    .departureLocations(List.of(dep))
                    .arrivalLocations(List.of(arr))
                    .passengers(List.of(adults))
                    .showOnlyNonStopFlight(false)
                    .acceptPendingProviders(false)
                    .forceFlightBundlePackage(false)
                    .disablePackageOfferTotalPrice(true)
                    .calculateFlightFees(false)
                    .culture("tr-TR")
                    .currency(request.getCurrency())
                    .build();

            String url = buildUrl(PRICE_SEARCH_PATH);
            HttpEntity<TourVisioFlightSearchRequest> entity = new HttpEntity<>(tvRequest, headers);

            log.info("[FlightApiClient] TourVisio uçuş arama isteği: {} (dep={}, arr={})", url, dep.getId(), arr.getId());
            ResponseEntity<TourVisioFlightSearchResponse> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, TourVisioFlightSearchResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null
                    && response.getBody().getBody() != null
                    && response.getBody().getBody().getFlights() != null) {
                List<TourVisioFlightSearchResponse.FlightGroup> groups = response.getBody().getBody().getFlights();
                if (roundTrip) {
                    return mapRoundTrip(groups, request.getDepartureDate().toString(), request.getReturnDate().toString());
                }
                return mapOneWay(groups);
            } else {
                log.warn("[FlightApiClient] TourVisio uçuş sonucu boş döndü, mesajlar: {}",
                        response.getBody() != null && response.getBody().getHeader() != null
                                ? response.getBody().getHeader().getMessages() : null);
                return List.of();
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (TourVisioAuthException e) {
            throw new TourVisioApiException("TourVisio'ya giriş yapılamadı veya yetkilendirme hatası: " + e.getMessage(), e);
        } catch (Exception e) {
            if (e instanceof TourVisioApiException) {
                throw (TourVisioApiException) e;
            }
            throw new TourVisioApiException("TourVisio FlightSearch integration exception: " + e.getMessage(), e);
        }
    }

    /**
     * TourVisio'nun tek bacaklik (leg) ucus bilgisini tasir (havayolu, saat,
     * aktarma, bagaj). Gidis-donus eslestirmesi icin hem "onceden paketlenmis"
     * hem "groupKeys ile eslestirilecek" senaryolarda kullanilir.
     */
    private static class Leg {
        String airline;
        String departureTime;
        String arrivalTime;
        String transfers;
        String baggage;
        Double price;
        String currency;

        String dateOnly() {
            return departureTime != null && departureTime.length() >= 10
                    ? departureTime.substring(0, 10) : "";
        }
    }

    private Leg toLeg(TourVisioFlightSearchResponse.FlightItem item) {
        Leg leg = new Leg();
        leg.airline = item.getMarketingAirline() != null ? item.getMarketingAirline().getName()
                : item.getAirline() != null ? item.getAirline().getName() : "Bilinmeyen Havayolu";
        leg.departureTime = item.getDeparture() != null ? item.getDeparture().getDate() : null;
        leg.arrivalTime = item.getArrival() != null ? item.getArrival().getDate() : null;

        int legCount = item.getSegments() != null ? item.getSegments().size() : 1;
        leg.transfers = legCount <= 1 ? "Direkt Uçuş" : (legCount - 1) + " Aktarmalı";

        leg.baggage = "Bilgi yok";
        if (item.getSegments() != null && !item.getSegments().isEmpty()
                && item.getSegments().get(0).getBaggageInformations() != null
                && !item.getSegments().get(0).getBaggageInformations().isEmpty()) {
            TourVisioFlightSearchResponse.BaggageInformation baggageInfo =
                    item.getSegments().get(0).getBaggageInformations().get(0);
            leg.baggage = ((int) baggageInfo.getWeight()) + "kg";
        }
        return leg;
    }

    /** Bir grubun "asil" teklifini dondurur: tekil {@code offer} varsa o,
     * yoksa {@code offers} listesindeki ilk teklif. */
    private TourVisioFlightSearchResponse.Offer resolveOffer(TourVisioFlightSearchResponse.FlightGroup group) {
        if (group.getOffer() != null) {
            return group.getOffer();
        }
        if (group.getOffers() != null && !group.getOffers().isEmpty()) {
            return group.getOffers().get(0);
        }
        return null;
    }

    private static class DedupedLegs {
        List<Leg> legs;
        List<String> keys;
    }

    /** Ayni fiziksel ucusu (havayolu+kalkis+varis saati) fiyata bakmaksizin
     * tekillestirir; ilk gorulen kopyayi (ve onun groupKeys'ini) tutar. */
    private DedupedLegs dedupeLegsByFlightIdentity(List<Leg> legs, List<String> groupKeysList) {
        DedupedLegs result = new DedupedLegs();
        result.legs = new ArrayList<>();
        result.keys = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < legs.size(); i++) {
            Leg leg = legs.get(i);
            String identity = leg.airline + "|" + leg.departureTime + "|" + leg.arrivalTime;
            if (seen.add(identity)) {
                result.legs.add(leg);
                result.keys.add(groupKeysList.get(i));
            }
        }
        return result;
    }

    // Fiyat kasten disaride birakiliyor: TourVisio ayni fiziksel ucusu kucuk
    // fiyat farklariyla birden fazla teklif olarak dondurebiliyor; bunlar
    // kullanici icin ayni sonuc sayilmali.
    private String dedupeKey(FlightSearchResponseItem item) {
        return item.getAirline() + "|" + item.getDepartureTime() + "|" + item.getArrivalTime() + "|"
                + item.getReturnAirline() + "|" + item.getReturnDepartureTime() + "|" + item.getReturnArrivalTime();
    }

    private List<FlightSearchResponseItem> dedupe(List<FlightSearchResponseItem> items) {
        List<FlightSearchResponseItem> result = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (FlightSearchResponseItem item : items) {
            if (seen.add(dedupeKey(item))) {
                result.add(item);
            }
        }
        return result;
    }

    private List<FlightSearchResponseItem> mapOneWay(List<TourVisioFlightSearchResponse.FlightGroup> groups) {
        List<FlightSearchResponseItem> result = new ArrayList<>();
        for (TourVisioFlightSearchResponse.FlightGroup group : groups) {
            if (group.getItems() == null || group.getItems().isEmpty()) {
                continue;
            }
            Leg leg = toLeg(group.getItems().get(0));
            TourVisioFlightSearchResponse.Offer offer = resolveOffer(group);

            result.add(FlightSearchResponseItem.builder()
                    .airline(leg.airline)
                    .departureTime(leg.departureTime)
                    .arrivalTime(leg.arrivalTime)
                    .transfers(leg.transfers)
                    .baggage(leg.baggage)
                    .price(offer != null && offer.getSingleAdultPrice() != null
                            ? offer.getSingleAdultPrice().getAmount() : null)
                    .currency(offer != null && offer.getSingleAdultPrice() != null
                            ? offer.getSingleAdultPrice().getCurrency() : null)
                    .build());
        }
        return dedupe(result);
    }

    /**
     * Gidis-donus sonuclarini eslestirir. Canli veriyle dogrulandi: TourVisio
     * cogu saglayici icin gidis+donusu AYNI grubun {@code items} dizisinde
     * iki eleman olarak ([0]=gidis, [1]=donus), TEK bir {@code offer} ve TEK
     * toplam fiyatla paketleyip donuyor — ayrica eslestirme yapmaya gerek yok.
     * Eger bir grup sadece 1 items eleman iceriyorsa (bazi saglayicilar gidis
     * ve donusu ayri grup olarak dondurebilir), docs-ai.santsg.com'daki
     * "groupKeys" mekanizmasiyla (ortak anahtar paylasan teklifler birlikte
     * satin alinabilir) tarih bazli eslestirme yedek olarak calisir.
     */
    private List<FlightSearchResponseItem> mapRoundTrip(
            List<TourVisioFlightSearchResponse.FlightGroup> groups, String checkIn, String checkOut) {
        List<FlightSearchResponseItem> result = new ArrayList<>();

        // Yedek eslestirme icin: tek bacakli gruplari tarihe gore biriktir.
        List<Leg> singleOutbound = new ArrayList<>();
        List<String> singleOutboundKeys = new ArrayList<>();
        List<Leg> singleInbound = new ArrayList<>();
        List<String> singleInboundKeys = new ArrayList<>();

        int bundledCount = 0;
        for (TourVisioFlightSearchResponse.FlightGroup group : groups) {
            if (group.getItems() == null || group.getItems().isEmpty()) {
                continue;
            }
            TourVisioFlightSearchResponse.Offer offer = resolveOffer(group);

            if (group.getItems().size() >= 2) {
                // Zaten paketlenmis gidis+donus.
                Leg outbound = toLeg(group.getItems().get(0));
                Leg inbound = toLeg(group.getItems().get(1));
                result.add(FlightSearchResponseItem.builder()
                        .airline(outbound.airline)
                        .departureTime(outbound.departureTime)
                        .arrivalTime(outbound.arrivalTime)
                        .transfers(outbound.transfers)
                        .baggage(outbound.baggage)
                        .returnAirline(inbound.airline)
                        .returnDepartureTime(inbound.departureTime)
                        .returnArrivalTime(inbound.arrivalTime)
                        .returnTransfers(inbound.transfers)
                        .returnBaggage(inbound.baggage)
                        .price(offer != null && offer.getSingleAdultPrice() != null
                                ? offer.getSingleAdultPrice().getAmount() : null)
                        .currency(offer != null && offer.getSingleAdultPrice() != null
                                ? offer.getSingleAdultPrice().getCurrency() : null)
                        .build());
                bundledCount++;
            } else {
                // Tek bacakli grup — groupKeys ile yedek eslestirmeye birik.
                Leg leg = toLeg(group.getItems().get(0));
                if (offer != null && offer.getSingleAdultPrice() != null) {
                    leg.price = offer.getSingleAdultPrice().getAmount();
                    leg.currency = offer.getSingleAdultPrice().getCurrency();
                }
                List<String> groupKeys = offer != null && offer.getGroupKeys() != null
                        ? offer.getGroupKeys() : List.of();
                if (leg.dateOnly().equals(checkIn)) {
                    singleOutbound.add(leg);
                    singleOutboundKeys.add(String.join(",", groupKeys));
                } else if (leg.dateOnly().equals(checkOut)) {
                    singleInbound.add(leg);
                    singleInboundKeys.add(String.join(",", groupKeys));
                }
            }
        }

        // TourVisio ham verisinde ayni fiziksel ucus (ayni havayolu+saat) birden
        // fazla teklif (offer) olarak, kucuk fiyat farklariyla tekrar edebiliyor.
        // Eslestirmeden once havayolu+saate gore tekillestiriyoruz (fiyati yok
        // sayarak) — yoksa her kopya ayri ayri eslesip sonuc sayisini sisiriyor.
        DedupedLegs dedupedOutbound = dedupeLegsByFlightIdentity(singleOutbound, singleOutboundKeys);
        DedupedLegs dedupedInbound = dedupeLegsByFlightIdentity(singleInbound, singleInboundKeys);
        singleOutbound = dedupedOutbound.legs;
        singleOutboundKeys = dedupedOutbound.keys;
        singleInbound = dedupedInbound.legs;
        singleInboundKeys = dedupedInbound.keys;

        int pairedCount = 0;
        for (int i = 0; i < singleOutbound.size(); i++) {
            Leg outbound = singleOutbound.get(i);
            List<String> outKeys = List.of(singleOutboundKeys.get(i).split(","));
            for (int j = 0; j < singleInbound.size(); j++) {
                List<String> inKeys = List.of(singleInboundKeys.get(j).split(","));
                if (outKeys.stream().noneMatch(inKeys::contains)) {
                    continue;
                }
                Leg inbound = singleInbound.get(j);
                Double totalPrice = (outbound.price != null && inbound.price != null)
                        ? outbound.price + inbound.price : null;
                result.add(FlightSearchResponseItem.builder()
                        .airline(outbound.airline)
                        .departureTime(outbound.departureTime)
                        .arrivalTime(outbound.arrivalTime)
                        .transfers(outbound.transfers)
                        .baggage(outbound.baggage)
                        .returnAirline(inbound.airline)
                        .returnDepartureTime(inbound.departureTime)
                        .returnArrivalTime(inbound.arrivalTime)
                        .returnTransfers(inbound.transfers)
                        .returnBaggage(inbound.baggage)
                        .price(totalPrice)
                        .currency(outbound.currency != null ? outbound.currency : inbound.currency)
                        .build());
                pairedCount++;
                break;
            }
        }

        List<FlightSearchResponseItem> deduped = dedupe(result);
        log.info("[FlightApiClient] Gidiş-dönüş eşleştirme: {} paketlenmiş, {} groupKeys ile eşleşmiş, "
                        + "tekilleştirme sonrası {} sonuç.",
                bundledCount, pairedCount, deduped.size());

        return deduped;
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
