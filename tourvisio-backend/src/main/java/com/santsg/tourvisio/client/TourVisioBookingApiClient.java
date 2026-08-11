package com.santsg.tourvisio.client;

import com.santsg.tourvisio.config.TourVisioConfig;
import com.santsg.tourvisio.dto.tourvisio.TourVisioBookingRequest;
import com.santsg.tourvisio.dto.tourvisio.TourVisioBookingResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Random;

@Component
@Slf4j
public class TourVisioBookingApiClient {

    private final TourVisioConfig config;
    private final TourVisioAuthService authService;
    private final RestTemplate restTemplate;

    public TourVisioBookingApiClient(TourVisioConfig config,
                                    TourVisioAuthService authService,
                                    @Qualifier("tourVisioRestTemplate") RestTemplate restTemplate) {
        this.config = config;
        this.authService = authService;
        this.restTemplate = restTemplate;
    }

    public TourVisioBookingResponse makeBooking(TourVisioBookingRequest request) {
        log.info("[TourVisioBookingApiClient] TourVisio GDS rezervasyon isteği işleniyor: {}", request);
        String reservationNum = "TV-" + (100000 + new Random().nextInt(900000));
        return TourVisioBookingResponse.builder()
                .success(true)
                .reservationNumber(reservationNum)
                .status("CONFIRMED")
                .message("TourVisio rezervasyonu başarıyla oluşturuldu.")
                .build();
    }
}
