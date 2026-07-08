package com.santsg.tourvisio.service;

import com.santsg.tourvisio.chat.SearchCriteria;
import com.santsg.tourvisio.client.TourVisioHotelApiClient;
import com.santsg.tourvisio.dto.ChatSearchResponse;
import com.santsg.tourvisio.dto.HotelSearchRequest;
import com.santsg.tourvisio.dto.HotelSearchResponseItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HotelSearchService {

    private static final Logger log = LoggerFactory.getLogger(HotelSearchService.class);

    private final TourVisioHotelApiClient hotelApiClient;

    public HotelSearchService(TourVisioHotelApiClient hotelApiClient) {
        this.hotelApiClient = hotelApiClient;
    }

    /**
     * Mevcut REST endpoint'ten çağrılan otel arama metodu.
     */
    public List<HotelSearchResponseItem> searchHotels(HotelSearchRequest request) {
        return hotelApiClient.searchHotels(request);
    }

    /**
     * Chat akışından çağrılan otel arama metodu.
     * SearchCriteria → HotelSearchRequest dönüşümü yapılır,
     * TourVisio API çağrılır ve sonuçlar ChatSearchResponse olarak döner.
     */
    public ChatSearchResponse searchFromCriteria(SearchCriteria criteria) {
        try {
            HotelSearchRequest request = criteria.toHotelSearchRequest();
            if (request == null) {
                log.warn("[HotelSearchService] SearchCriteria → HotelSearchRequest dönüşümü başarısız — eksik alanlar var.");
                return ChatSearchResponse.builder()
                        .reply("Otel araması için gerekli bilgiler eksik. Lütfen tekrar deneyin.")
                        .searchType("HOTEL_SEARCH")
                        .success(false)
                        .results(List.of())
                        .build();
            }

            List<HotelSearchResponseItem> results = hotelApiClient.searchHotels(request);

            if (results == null || results.isEmpty()) {
                return ChatSearchResponse.builder()
                        .reply("Belirttiğiniz kriterlere uygun otel bulunamadı. Farklı tarih veya lokasyon deneyebilirsiniz.")
                        .searchType("HOTEL_SEARCH")
                        .success(true)
                        .results(List.of())
                        .build();
            }

            String location = criteria.getLocationOrHotelName();
            String reply = location + " için " + results.size() + " otel bulundu.";

            return ChatSearchResponse.builder()
                    .reply(reply)
                    .searchType("HOTEL_SEARCH")
                    .success(true)
                    .results(results)
                    .build();

        } catch (Exception e) {
            log.error("[HotelSearchService] Otel aramasında hata: {}", e.getMessage(), e);
            return ChatSearchResponse.builder()
                    .reply("Otel arama servisi şu anda kullanılamıyor, lütfen daha sonra tekrar deneyin.")
                    .searchType("HOTEL_SEARCH")
                    .success(false)
                    .results(List.of())
                    .build();
        }
    }
}

