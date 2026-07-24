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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class HotelSearchService {

    private static final Logger log = LoggerFactory.getLogger(HotelSearchService.class);

    /** Sonuç bulunamadığında denenecek gün ofsetleri, isteğe en yakından uzağa doğru. */
    private static final int[] NEARBY_DATE_OFFSETS = {1, -1, 2, -2, 3, -3};
    private static final int MAX_SUGGESTED_DATES = 3;
    private static final DateTimeFormatter DISPLAY_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /**
     * TourVisio pricesearch bazen gerçekte dolu olan bir rota/tarih için de
     * boş sonuç döndürebiliyor (uçuş aramasında da aynı davranış gözlemlendi,
     * bkz. FlightSearchService). Kullanıcıya "sonuç yok" demeden önce aynı
     * aramayı kısa bir bekleme ile birkaç kez daha deniyoruz.
     */
    private static final int EMPTY_RESULT_RETRY_COUNT = 2;
    private static final long EMPTY_RESULT_RETRY_DELAY_MS = 600;

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

    private List<HotelSearchResponseItem> searchWithRetry(SearchCriteria criteria) {
        List<HotelSearchResponseItem> results = hotelApiClient.searchHotelsFromCriteria(criteria);
        for (int attempt = 1; (results == null || results.isEmpty()) && attempt <= EMPTY_RESULT_RETRY_COUNT; attempt++) {
            log.warn("[HotelSearchService] Boş sonuç, {}. tekrar deneme yapılıyor ({})",
                    attempt, criteria.getLocationOrHotelName());
            try {
                Thread.sleep(EMPTY_RESULT_RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            results = hotelApiClient.searchHotelsFromCriteria(criteria);
        }
        return results;
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
            List<HotelSearchResponseItem> results = searchWithRetry(criteria);

            if (results == null || results.isEmpty()) {
                return buildNoResultsResponse(criteria, locale);
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

    /**
     * Kullanıcı yeni bir kriter vermeden (ör. "en yakın tarih ne var") sadece
     * yakın tarih önerisi istediğinde çağrılır. Zaten başarısız olduğu bilinen
     * orijinal tarihi tekrar aramadan, doğrudan yakın tarihleri dener —
     * {@link #searchFromCriteria} ile kıyasla bir gereksiz arama isteği daha az.
     */
    public ChatSearchResponse suggestNearbyDatesOnly(SearchCriteria criteria) {
        Locale locale = LocaleResolver.resolveLocale(criteria);
        return buildNoResultsResponse(criteria, locale);
    }

    /**
     * Kriterlere uygun otel bulunamadığında en yakın uygun tarihleri de
     * önererek cevap oluşturur.
     *
     * <p>TourVisio'nun genel "getcheckindates" takvimi kişi/oda sayısını hesaba
     * katmıyor — sadece o lokasyonda o gün herhangi bir şey satılabiliyor mu
     * diye bakıyor. Bu yüzden zaten başarısız olmuş (ör. kalabalık grup için
     * oda bulunamayan) bir tarihi bile "uygun" diye önerebiliyordu. Bunun
     * yerine, kullanıcının GERÇEK kriterleriyle (aynı yetişkin/çocuk sayısı)
     * yakın günlerde gerçekten arama yaparak sadece fiilen sonuç veren
     * tarihleri öneriyoruz — uçuş tarafındaki yöntemle aynı mantık.</p>
     */
    private ChatSearchResponse buildNoResultsResponse(SearchCriteria criteria, Locale locale) {
        List<LocalDate> nearbyDates = findNearbyAvailableDates(criteria);

        List<String> suggestedDates = nearbyDates.stream()
                .map(LocalDate::toString)
                .collect(Collectors.toList());

        String reply;
        if (!suggestedDates.isEmpty()) {
            String datesText = nearbyDates.stream()
                    .map(DISPLAY_DATE_FORMAT::format)
                    .collect(Collectors.joining(", "));
            reply = messageSource.getMessage("hotel.search.no.results.with.dates", new Object[]{datesText}, locale);
        } else {
            reply = messageSource.getMessage("hotel.search.no.results", null, locale);
        }

        return ChatSearchResponse.builder()
                .reply(reply)
                .searchType("HOTEL_SEARCH")
                .success(true)
                .results(List.of())
                .suggestedDates(suggestedDates)
                .build();
    }

    private List<LocalDate> findNearbyAvailableDates(SearchCriteria baseCriteria) {
        List<LocalDate> found = new ArrayList<>();
        if (baseCriteria.getCheckInDate() == null || baseCriteria.getCheckOutDate() == null) {
            return found;
        }

        long nights = ChronoUnit.DAYS.between(baseCriteria.getCheckInDate(), baseCriteria.getCheckOutDate());
        if (nights <= 0) nights = 1;

        for (int offset : NEARBY_DATE_OFFSETS) {
            if (found.size() >= MAX_SUGGESTED_DATES) break;

            LocalDate candidateCheckIn = baseCriteria.getCheckInDate().plusDays(offset);
            if (candidateCheckIn.isBefore(LocalDate.now())) continue;

            SearchCriteria candidate = cloneCriteria(baseCriteria);
            candidate.setCheckInDate(candidateCheckIn);
            candidate.setCheckOutDate(candidateCheckIn.plusDays(nights));

            try {
                List<HotelSearchResponseItem> candidateResults = hotelApiClient.searchHotelsFromCriteria(candidate);
                if (candidateResults != null && !candidateResults.isEmpty()) {
                    found.add(candidateCheckIn);
                }
            } catch (Exception e) {
                log.warn("[HotelSearchService] Alternatif tarih denemesi başarısız ({}): {}", candidateCheckIn, e.getMessage());
            }
        }

        found.sort(Comparator.naturalOrder());
        return found;
    }

    private SearchCriteria cloneCriteria(SearchCriteria src) {
        SearchCriteria c = new SearchCriteria();
        c.setSearchType(src.getSearchType());
        c.setCurrency(src.getCurrency());
        c.setPreferredLanguage(src.getPreferredLanguage());
        c.setCountry(src.getCountry());
        c.setLocationOrHotelName(src.getLocationOrHotelName());
        c.setCheckInDate(src.getCheckInDate());
        c.setCheckOutDate(src.getCheckOutDate());
        c.setAdultCount(src.getAdultCount());
        c.setChildCount(src.getChildCount());
        c.setChildAges(src.getChildAges());
        c.setNationality(src.getNationality());
        c.setRoomCount(src.getRoomCount());
        return c;
    }

    public com.santsg.tourvisio.dto.tourvisio.GetCheckInDatesResponse getCheckInDates(com.santsg.tourvisio.dto.tourvisio.GetCheckInDatesRequest request) {
        return hotelApiClient.getCheckInDates(request);
    }

    public com.santsg.tourvisio.dto.tourvisio.GetProductInfoResponse getProductInfo(com.santsg.tourvisio.dto.tourvisio.GetProductInfoRequest request) {
        return hotelApiClient.getProductInfo(request);
    }
}

