package com.santsg.tourvisio.dto.tourvisio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * TourVisio GetArrivalAutocomplete response yapısı.
 *
 * <p>Gerçek API response'u:
 * <pre>
 * {
 *   "header": { "requestId": "...", "success": true },
 *   "body": {
 *     "items": [
 *       {
 *         "type": 1,
 *         "geolocation": { "longitude": "30.70838", "latitude": "36.87536" },
 *         "country": { "id": "TR", "name": "Turkey" },
 *         "state": { "id": "10828", "name": "Turkish Riviera" },
 *         "city": { "id": "23494", "name": "Antalya" },
 *         "provider": 2
 *       }
 *     ]
 *   }
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TourVisioAutocompleteResponse {
    private Header header;
    private Body body;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Header {
        private String requestId;
        private boolean success;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Body {
        private List<AutocompleteItem> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AutocompleteItem {
        private int type;
        private IdName city;
        private IdName state;
        private IdName country;
        private IdName hotel;
        private int provider;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class IdName {
            private String id;
            private String name;
        }
    }
}
