package com.santsg.tourvisio.agent;

import com.santsg.tourvisio.chat.SearchCriteria;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// LLM üst seviyeye de şemada olmayan alanlar ekleyebiliyor; bilinmeyen alan
// tüm çıkarımı geçersiz kılmasın diye yok sayılıyor (bkz. SearchCriteria).
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractionResult {
    private String intent; // HOTEL_SEARCH, FLIGHT_SEARCH, UNKNOWN, OUT_OF_SCOPE
    private SearchCriteria criteria;
}
