package com.santsg.tourvisio.service;

import com.santsg.tourvisio.client.TourVisioHotelApiClient;
import com.santsg.tourvisio.dto.ArrivalAutocompleteResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ArrivalAutocompleteService {

    private final TourVisioHotelApiClient hotelApiClient;

    public ArrivalAutocompleteService(TourVisioHotelApiClient hotelApiClient) {
        this.hotelApiClient = hotelApiClient;
    }

    public List<ArrivalAutocompleteResponse> getArrivalAutocomplete(String query) {
        return hotelApiClient.getArrivalAutocomplete(query);
    }
}
