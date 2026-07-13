package com.santsg.tourvisio.agent;

import com.santsg.tourvisio.chat.SearchCriteria;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractionResult {
    private String intent; // HOTEL_SEARCH, FLIGHT_SEARCH, UNKNOWN, OUT_OF_SCOPE
    private SearchCriteria criteria;
}
