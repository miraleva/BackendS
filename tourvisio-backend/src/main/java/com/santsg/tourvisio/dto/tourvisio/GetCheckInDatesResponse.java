package com.santsg.tourvisio.dto.tourvisio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Response DTO for TourVisio GetCheckInDates API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetCheckInDatesResponse {

    @JsonProperty("body")
    private Body body;

    /** Diğer TourVisio çağırımlarıyla tutarlılık için düz erişim; body.dates'e delege eder. */
    public List<String> getDates() {
        return body != null ? body.getDates() : null;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Body {
        @JsonProperty("dates")
        private List<String> dates;
    }
}
