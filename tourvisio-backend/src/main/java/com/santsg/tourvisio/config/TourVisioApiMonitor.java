package com.santsg.tourvisio.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TourVisioApiMonitor {

    private static final int MAX_LOGS = 50;
    private static final ConcurrentLinkedQueue<ApiLog> logs = new ConcurrentLinkedQueue<>();
    
    // Track average latencies per endpoint type
    private static final Map<String, List<Long>> latencyTracks = new HashMap<>();

    static {
        latencyTracks.put("HotelSearch", Collections.synchronizedList(new ArrayList<>()));
        latencyTracks.put("FlightSearch", Collections.synchronizedList(new ArrayList<>()));
        latencyTracks.put("Auth", Collections.synchronizedList(new ArrayList<>()));
        latencyTracks.put("Detail", Collections.synchronizedList(new ArrayList<>()));
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiLog {
        private String timestamp;
        private String method;
        private String uri;
        private String endpointType;
        private long latencyMs;
        private int statusCode;
        private String statusText;
        private String errorMessage;
        private boolean success;
    }

    public static void logCall(String method, String uri, long latencyMs, int statusCode, String statusText, String errorMessage, boolean success) {
        String endpointType = getEndpointType(uri);
        
        // Track latency
        List<Long> latencies = latencyTracks.get(endpointType);
        if (latencies != null) {
            latencies.add(latencyMs);
            if (latencies.size() > 20) {
                latencies.remove(0);
            }
        }

        // Add to log queue
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
                .build();

        logs.add(log);
        while (logs.size() > MAX_LOGS) {
            logs.poll();
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
        List<ApiLog> list = new ArrayList<>(logs);
        Collections.reverse(list);
        return list;
    }

    public static Map<String, Long> getAverageLatencies() {
        Map<String, Long> avgs = new HashMap<>();
        for (Map.Entry<String, List<Long>> entry : latencyTracks.entrySet()) {
            List<Long> list = entry.getValue();
            synchronized (list) {
                if (list.isEmpty()) {
                    // Provide realistic initial latency seed if no calls have run yet
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
