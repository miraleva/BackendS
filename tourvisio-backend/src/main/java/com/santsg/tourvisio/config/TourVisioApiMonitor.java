package com.santsg.tourvisio.config;

import com.santsg.tourvisio.entity.ApiLog;
import com.santsg.tourvisio.repository.ApiLogRepository;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.*;

@Component
public class TourVisioApiMonitor {

    private static ApiLogRepository apiLogRepository;

    public TourVisioApiMonitor(ApiLogRepository apiLogRepository) {
        TourVisioApiMonitor.apiLogRepository = apiLogRepository;
    }

    private static final Map<String, List<Long>> latencyTracks = new HashMap<>();

    static {
        latencyTracks.put("HotelSearch", Collections.synchronizedList(new ArrayList<>()));
        latencyTracks.put("FlightSearch", Collections.synchronizedList(new ArrayList<>()));
        latencyTracks.put("Auth", Collections.synchronizedList(new ArrayList<>()));
        latencyTracks.put("Detail", Collections.synchronizedList(new ArrayList<>()));
    }

    public static void logCall(String method, String uri, long latencyMs, int statusCode, String statusText, String errorMessage, boolean success, String requestPayload, String responsePayload) {
        String endpointType = getEndpointType(uri);
        
        // Track latency
        List<Long> latencies = latencyTracks.get(endpointType);
        if (latencies != null) {
            latencies.add(latencyMs);
            if (latencies.size() > 20) {
                latencies.remove(0);
            }
        }

        // Persist to DB
        ApiLog log = ApiLog.builder()
                .timestamp(LocalDateTime.now().toString())
                .method(method)
                .uri(uri)
                .endpointType(endpointType)
                .latencyMs(latencyMs)
                .statusCode(statusCode)
                .statusText(statusText)
                .errorMessage(errorMessage)
                .success(success)
                .requestPayload(requestPayload)
                .responsePayload(responsePayload)
                .build();

        if (apiLogRepository != null) {
            try {
                apiLogRepository.save(log);
            } catch (Exception e) {
                System.err.println("[TourVisioApiMonitor] Failed to persist log: " + e.getMessage());
            }
        }
    }

    private static String getEndpointType(String uri) {
        if (uri.contains("/hotels/search") || uri.contains("/hotelsearch")) return "HotelSearch";
        if (uri.contains("/flights/search") || uri.contains("/flightsearch")) return "FlightSearch";
        if (uri.contains("/authenticationservice/login") || uri.contains("/auth")) return "Auth";
        if (uri.contains("/details") || uri.contains("/hoteldetails") || uri.contains("/getdetails")) return "Detail";
        return "Other";
    }

    public static List<ApiLog> getLogs() {
        if (apiLogRepository != null) {
            try {
                return apiLogRepository.findTop50ByOrderByTimestampDesc();
            } catch (Exception e) {
                System.err.println("[TourVisioApiMonitor] Failed to fetch logs: " + e.getMessage());
            }
        }
        return Collections.emptyList();
    }

    public static Map<String, Long> getAverageLatencies() {
        Map<String, Long> avgs = new HashMap<>();
        for (Map.Entry<String, List<Long>> entry : latencyTracks.entrySet()) {
            List<Long> list = entry.getValue();
            synchronized (list) {
                if (list.isEmpty()) {
                    if ("HotelSearch".equals(entry.getKey())) avgs.put(entry.getKey(), 460L);
                    else if ("FlightSearch".equals(entry.getKey())) avgs.put(entry.getKey(), 620L);
                    else if ("Auth".equals(entry.getKey())) avgs.put(entry.getKey(), 320L);
                    else avgs.put(entry.getKey(), 180L);
                } else {
                    double avg = list.stream().mapToLong(Long::longValue).average().orElse(0.0);
                    avgs.put(entry.getKey(), Math.round(avg));
                }
            }
        }
        return avgs;
    }
}
