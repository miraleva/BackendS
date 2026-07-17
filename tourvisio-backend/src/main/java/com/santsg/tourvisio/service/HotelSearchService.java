package com.santsg.tourvisio.service;

import com.santsg.tourvisio.chat.SearchCriteria;
import com.santsg.tourvisio.client.TourVisioHotelApiClient;
import com.santsg.tourvisio.dto.ChatSearchResponse;
import com.santsg.tourvisio.dto.HotelSearchRequest;
import com.santsg.tourvisio.dto.HotelSearchResponseItem;
import com.santsg.tourvisio.util.LocaleResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class HotelSearchService {

    private static final Logger log = LoggerFactory.getLogger(HotelSearchService.class);

    private final TourVisioHotelApiClient hotelApiClient;
    private final MessageSource messageSource;

    public HotelSearchService(TourVisioHotelApiClient hotelApiClient, MessageSource messageSource) {
        this.hotelApiClient = hotelApiClient;
        this.messageSource = messageSource;
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
        Locale locale = LocaleResolver.resolveLocale(criteria);
        try {
            // Önce SearchCriteria'dan doğrudan arama yap
            // (culture, dil, para birimi criteria'dan okunur)
            List<HotelSearchResponseItem> results = hotelApiClient.searchHotelsFromCriteria(criteria);

            if (results == null || results.isEmpty()) {
                return ChatSearchResponse.builder()
                        .reply(messageSource.getMessage("hotel.search.no.results", null, locale))
                        .searchType("HOTEL_SEARCH")
                        .success(true)
                        .results(List.of())
                        .build();
            }

            String location = criteria.getLocationOrHotelName();
            String currency = criteria.getCurrency() != null ? criteria.getCurrency() : "EUR";

            // En iyi teklifi öne çıkar
            HotelSearchResponseItem best = results.get(0);
            String bestInfo = String.format("%s (%d★) — %s — %.2f %s",
                    best.getName(),
                    best.getStars() != null ? best.getStars() : 0,
                    best.getBoardType() != null ? best.getBoardType() : best.getPensionType(),
                    best.getPrice() != null ? best.getPrice() : 0.0,
                    currency);

            String reply = messageSource.getMessage("hotel.search.success",
                    new Object[]{results.size(), location, bestInfo}, locale);

            return ChatSearchResponse.builder()
                    .reply(reply)
                    .searchType("HOTEL_SEARCH")
                    .success(true)
                    .results(results)
                    .build();

        } catch (Exception e) {
            log.error("[HotelSearchService] Otel aramasında hata: {}", e.getMessage(), e);
            return ChatSearchResponse.builder()
                    .reply(messageSource.getMessage("hotel.search.error", null, locale))
                    .searchType("HOTEL_SEARCH")
                    .success(false)
                    .results(List.of())
                    .build();
        }
    }

    public com.santsg.tourvisio.dto.tourvisio.GetCheckInDatesResponse getCheckInDates(com.santsg.tourvisio.dto.tourvisio.GetCheckInDatesRequest request) {
        return hotelApiClient.getCheckInDates(request);
    }

    public com.santsg.tourvisio.dto.tourvisio.GetProductInfoResponse getProductInfo(com.santsg.tourvisio.dto.tourvisio.GetProductInfoRequest request) {
        return hotelApiClient.getProductInfo(request);
    }
}

