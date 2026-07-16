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

import java.util.List;
import java.util.Locale;

@Service
public class FlightSearchService {

    private static final Logger log = LoggerFactory.getLogger(FlightSearchService.class);

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
                return ChatSearchResponse.builder()
                        .reply(messageSource.getMessage("flight.search.no.results", null, locale))
                        .searchType("FLIGHT_SEARCH")
                        .success(true)
                        .results(List.of())
                        .build();
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
}
