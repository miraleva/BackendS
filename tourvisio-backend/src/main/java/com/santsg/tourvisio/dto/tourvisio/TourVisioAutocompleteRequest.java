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
public class TourVisioAutocompleteRequest {
    private int productType; // typically 2
    private String query;
    private List<Integer> masterProductTypes;
    private String culture;
}
