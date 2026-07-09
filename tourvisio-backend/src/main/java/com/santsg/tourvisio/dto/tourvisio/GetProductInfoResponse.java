package com.santsg.tourvisio.dto.tourvisio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Response DTO for TourVisio GetProductInfo API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetProductInfoResponse {

    @JsonProperty("header")
    private Header header;

    @JsonProperty("body")
    private Body body;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Header {
        private String requestId;
        private boolean success;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Body {
        private HotelInfo hotel;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HotelInfo {
        private String id;
        private String name;
        private String code;
        private int provider;
        private String address;
        private String thumbnail;
        private String thumbnailFull;
        private int stars;
        private Geolocation geolocation;
        private LocationInfo country;
        private LocationInfo city;
        private LocationInfo town;
        private List<Season> seasons;
        private List<MediaFile> mediaFiles;
        private List<Theme> themes;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Geolocation {
        private String longitude;
        private String latitude;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LocationInfo {
        private String id;
        private String name;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Season {
        private int id;
        private String name;
        private String beginDate;
        private String endDate;
        private List<TextCategory> textCategories;
        private List<FacilityCategory> facilityCategories;
        private List<MediaFile> mediaFiles;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TextCategory {
        private String code;
        private String name;
        private List<Presentation> presentations;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Presentation {
        private int textType;
        private String text;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FacilityCategory {
        private String id;
        private String name;
        private List<Facility> facilities;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Facility {
        private String id;
        private String name;
        private String note;
        private boolean isPriced;
        private boolean highlighted;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MediaFile {
        private int fileType;
        private String url;
        private String urlFull;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Theme {
        private String id;
        private String name;
    }
}
