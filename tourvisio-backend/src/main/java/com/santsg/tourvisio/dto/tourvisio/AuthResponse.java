package com.santsg.tourvisio.dto.tourvisio;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Standard TourVisio Authentication login response structure.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    @JsonProperty("header")
    private Header header;

    @JsonProperty("body")
    private Body body;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Header {
        private String requestId;
        private boolean success;
        private List<Message> messages;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private int id;
        private String code;
        private int messageType;
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Body {
        private String token;
        private String expiresOn;
        private long tokenId;
        private UserInfo userInfo;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private String code;
        private String name;
        private Object mainAgency;
        private Agency agency;
        private Office office;
        private Operator operator;
        private Market market;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Agency {
        private String code;
        private String name;
        private String registerCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Office {
        private String code;
        private String name;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Operator {
        private String code;
        private String name;
        private String thumbnail;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Market {
        private String code;
        private String name;
        private String favicon;
    }
}
