package com.santsg.tourvisio.service;

import com.santsg.tourvisio.chat.SearchCriteria;
import com.santsg.tourvisio.client.TourVisioFlightApiClient;
import com.santsg.tourvisio.dto.ChatSearchResponse;
import com.santsg.tourvisio.dto.FlightSearchRequest;
import com.santsg.tourvisio.dto.FlightSearchResponseItem;
import com.santsg.tourvisio.util.LocaleResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class FlightSearchService {

    private static final Logger log = LoggerFactory.getLogger(FlightSearchService.class);

    /** Sonuç bulunamadığında denenecek gün ofsetleri, isteğe en yakından uzağa doğru. */
    private static final int[] NEARBY_DATE_OFFSETS = {1, -1, 2, -2, 3, -3};
    private static final int MAX_SUGGESTED_DATES = 3;
    private static final DateTimeFormatter DISPLAY_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final TourVisioFlightApiClient flightApiClient;
    private final MessageSource messageSource;

    public FlightSearchService(TourVisioFlightApiClient flightApiClient, MessageSource messageSource) {
        this.flightApiClient = flightApiClient;
        this.messageSource = messageSource;
    }

    public List<FlightSearchResponseItem> searchFlights(FlightSearchRequest request) {
        return flightApiClient.searchFlights(request);
    }

    public ChatSearchResponse searchFromCriteria(SearchCriteria criteria) {
        Locale locale = LocaleResolver.resolveLocale(criteria);
        try {
            FlightSearchRequest request = criteria.toFlightSearchRequest();
            if (request == null) {
                log.warn("[FlightSearchService] SearchCriteria → FlightSearchRequest dönüşümü başarısız — eksik alanlar var.");
                return ChatSearchResponse.builder()
                        .reply(messageSource.getMessage("flight.search.missing.info", null, locale))
                        .searchType("FLIGHT_SEARCH")
                        .success(false)
                        .results(List.of())
                        .build();
            }

            List<FlightSearchResponseItem> results = flightApiClient.searchFlights(request);
            if (results == null || results.isEmpty()) {
                return buildNoResultsResponse(request, locale);
            }

            FlightSearchResponseItem best = results.get(0);
            String bestInfo = String.format("%s — %.2f %s",
                    best.getAirline(),
                    best.getPrice() != null ? best.getPrice() : 0.0,
                    best.getCurrency());
            String reply = messageSource.getMessage("flight.search.success",
                    new Object[]{results.size(), criteria.getDepartureLocation(), criteria.getArrivalLocation(), bestInfo},
                    locale);

            return ChatSearchResponse.builder()
                    .reply(reply)
                    .searchType("FLIGHT_SEARCH")
                    .success(true)
                    .results(results)
                    .build();
        } catch (Exception e) {
            log.error("[FlightSearchService] Uçuş aramasında hata: {}", e.getMessage(), e);
            return ChatSearchResponse.builder()
                    .reply(messageSource.getMessage("flight.search.error", null, locale))
                    .searchType("FLIGHT_SEARCH")
                    .success(false)
                    .results(List.of())
                    .build();
        }
    }

    /**
     * Kullanıcı yeni bir kriter vermeden (ör. "en yakın tarih ne var") sadece
     * yakın tarih önerisi istediğinde çağrılır. Zaten başarısız olduğu bilinen
     * orijinal tarihi tekrar aramadan, doğrudan yakın tarihleri dener.
     */
    public ChatSearchResponse suggestNearbyDatesOnly(SearchCriteria criteria) {
        Locale locale = LocaleResolver.resolveLocale(criteria);
        FlightSearchRequest request = criteria.toFlightSearchRequest();
        if (request == null) {
            return ChatSearchResponse.builder()
                    .reply(messageSource.getMessage("flight.search.missing.info", null, locale))
                    .searchType("FLIGHT_SEARCH")
                    .success(false)
                    .results(List.of())
                    .build();
        }
        return buildNoResultsResponse(request, locale);
    }

    /**
     * Kriterlere uygun uçuş bulunamadığında, TourVisio'da uçuşlar için otelin
     * "getcheckindates" API'sine denk bir tarih-uygunluk servisi olmadığından,
     * istenen tarihe yakın birkaç günü (±1, ±2, ±3) gerçek pricesearch çağrısıyla
     * deneyip sonuç veren tarihleri önerir. Uydurma tarih döndürmez — sadece
     * gerçekten uçuş bulunan tarihleri önerir.
     */
    private ChatSearchResponse buildNoResultsResponse(FlightSearchRequest baseRequest, Locale locale) {
        List<LocalDate> nearbyDates = findNearbyAvailableDates(baseRequest);

        List<String> suggestedDates = nearbyDates.stream()
                .map(LocalDate::toString)
                .collect(Collectors.toList());

        String reply;
        if (!suggestedDates.isEmpty()) {
            String datesText = nearbyDates.stream()
                    .map(DISPLAY_DATE_FORMAT::format)
                    .collect(Collectors.joining(", "));
            reply = messageSource.getMessage("flight.search.no.results.with.dates", new Object[]{datesText}, locale);
        } else {
            reply = messageSource.getMessage("flight.search.no.results", null, locale);
        }

        return ChatSearchResponse.builder()
                .reply(reply)
                .searchType("FLIGHT_SEARCH")
                .success(true)
                .results(List.of())
                .suggestedDates(suggestedDates)
                .build();
    }

    private List<LocalDate> findNearbyAvailableDates(FlightSearchRequest baseRequest) {
        List<LocalDate> found = new ArrayList<>();
        if (baseRequest.getDepartureDate() == null) {
            return found;
        }

        Long tripLengthDays = baseRequest.getReturnDate() != null
                ? ChronoUnit.DAYS.between(baseRequest.getDepartureDate(), baseRequest.getReturnDate())
                : null;

        for (int offset : NEARBY_DATE_OFFSETS) {
            if (found.size() >= MAX_SUGGESTED_DATES) break;

            LocalDate candidateDeparture = baseRequest.getDepartureDate().plusDays(offset);
            if (candidateDeparture.isBefore(LocalDate.now())) continue;

            FlightSearchRequest candidate = new FlightSearchRequest();
            candidate.setDepartureLocation(baseRequest.getDepartureLocation());
            candidate.setArrivalLocation(baseRequest.getArrivalLocation());
            candidate.setDepartureAirport(baseRequest.getDepartureAirport());
            candidate.setArrivalAirport(baseRequest.getArrivalAirport());
            candidate.setPassengerCount(baseRequest.getPassengerCount());
            candidate.setTripType(baseRequest.getTripType());
            candidate.setCurrency(baseRequest.getCurrency());
            candidate.setDepartureDate(candidateDeparture);
            if (tripLengthDays != null) {
                candidate.setReturnDate(candidateDeparture.plusDays(tripLengthDays));
            }

            try {
                List<FlightSearchResponseItem> candidateResults = flightApiClient.searchFlights(candidate);
                if (candidateResults != null && !candidateResults.isEmpty()) {
                    found.add(candidateDeparture);
                }
            } catch (Exception e) {
                log.warn("[FlightSearchService] Alternatif tarih denemesi başarısız ({}): {}", candidateDeparture, e.getMessage());
            }
        }

        found.sort(Comparator.naturalOrder());
        return found;
    }
}
