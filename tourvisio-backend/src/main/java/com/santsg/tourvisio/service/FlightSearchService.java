package com.santsg.tourvisio.service;

import com.santsg.tourvisio.chat.SearchCriteria;
import com.santsg.tourvisio.client.TourVisioFlightApiClient;
import com.santsg.tourvisio.dto.ChatSearchResponse;
import com.santsg.tourvisio.dto.FlightSearchRequest;
import com.santsg.tourvisio.dto.FlightSearchResponseItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FlightSearchService {

    private static final Logger log = LoggerFactory.getLogger(FlightSearchService.class);

    private final TourVisioFlightApiClient flightApiClient;

    public FlightSearchService(TourVisioFlightApiClient flightApiClient) {
        this.flightApiClient = flightApiClient;
    }

    public List<FlightSearchResponseItem> searchFlights(FlightSearchRequest request) {
        return flightApiClient.searchFlights(request);
    }

    public ChatSearchResponse searchFromCriteria(SearchCriteria criteria) {
        try {
            FlightSearchRequest request = criteria.toFlightSearchRequest();
            if (request == null) {
                log.warn("[FlightSearchService] SearchCriteria → FlightSearchRequest dönüşümü başarısız — eksik alanlar var.");
                return ChatSearchResponse.builder()
                        .reply("Uçuş araması için gerekli bilgiler eksik. Lütfen tekrar deneyin.")
                        .searchType("FLIGHT_SEARCH")
                        .success(false)
                        .results(List.of())
                        .build();
            }

            List<FlightSearchResponseItem> results = flightApiClient.searchFlights(request);
            if (results == null || results.isEmpty()) {
                return ChatSearchResponse.builder()
                        .reply("Belirttiğiniz kriterlere uygun uçuş bulunamadı. Farklı tarih veya rota deneyebilirsiniz.")
                        .searchType("FLIGHT_SEARCH")
                        .success(true)
                        .results(List.of())
                        .build();
            }

            return ChatSearchResponse.builder()
                    .reply("Belirttiğiniz kriterlere uygun uçuşlar bulundu.")
                    .searchType("FLIGHT_SEARCH")
                    .success(true)
                    .results(results)
                    .build();
        } catch (Exception e) {
            log.error("[FlightSearchService] Uçuş aramasında hata: {}", e.getMessage(), e);
            return ChatSearchResponse.builder()
                    .reply("Uçuş arama servisi şu anda kullanılamıyor, lütfen daha sonra tekrar deneyin.")
                    .searchType("FLIGHT_SEARCH")
                    .success(false)
                    .results(List.of())
                    .build();
        }
    }
}
