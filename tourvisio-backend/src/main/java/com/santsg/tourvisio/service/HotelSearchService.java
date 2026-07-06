package com.santsg.tourvisio.service;

import com.santsg.tourvisio.client.TourVisioHotelApiClient;
import com.santsg.tourvisio.dto.HotelSearchRequest;
import com.santsg.tourvisio.dto.HotelSearchResponseItem;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class HotelSearchService {

    private final TourVisioHotelApiClient hotelApiClient;

    public HotelSearchService(TourVisioHotelApiClient hotelApiClient) {
        this.hotelApiClient = hotelApiClient;
    }

    public List<HotelSearchResponseItem> searchHotels(HotelSearchRequest request) {
        return hotelApiClient.searchHotels(request);
    }
}
