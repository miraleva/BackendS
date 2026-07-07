package com.santsg.tourvisio.dto.tourvisio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TourVisioAutocompleteResponse {
    private Header header;
    private Body body;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Header {
        private String requestId;
        private boolean success;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Body {
        private List<AutocompleteItem> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AutocompleteItem {
        private int type;
        private City city;
        private Hotel hotel;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class City {
            private String id;
            private String name;
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Hotel {
            private String id;
            private String name;
        }
    }
}
