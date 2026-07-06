package com.santsg.tourvisio.service;

import com.santsg.tourvisio.client.TourVisioFlightApiClient;
import com.santsg.tourvisio.dto.FlightSearchRequest;
import com.santsg.tourvisio.dto.FlightSearchResponseItem;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class FlightSearchService {

    private final TourVisioFlightApiClient flightApiClient;

    public FlightSearchService(TourVisioFlightApiClient flightApiClient) {
        this.flightApiClient = flightApiClient;
    }

    public List<FlightSearchResponseItem> searchFlights(FlightSearchRequest request) {
        return flightApiClient.searchFlights(request);
    }
}
