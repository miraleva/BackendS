package com.santsg.tourvisio.agent;

import com.santsg.tourvisio.client.AIFallbackChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExtractionAgentTest {

    @Mock
    private AIFallbackChain geminiExtractionClient;

    @InjectMocks
    private ExtractionAgent extractionAgent;

    @Test
    void extract_shouldReturnValidExtractionResult_whenOpenAiReturnsValidJson() {
        String mockResponse = """
                {
                  "intent": "HOTEL_SEARCH",
                  "criteria": {
                    "locationOrHotelName": "Antalya",
                    "checkInDate": "2026-07-15",
                    "checkOutDate": "2026-07-20",
                    "adultCount": 2,
                    "currency": "EUR"
                  }
                }
                """;
        when(geminiExtractionClient.complete(anyString())).thenReturn(mockResponse);

        ExtractionResult result = extractionAgent.extract("Hotel in Antalya for 2 adults in EUR from July 15 to 20", null, null, null);

        assertThat(result).isNotNull();
        assertThat(result.getIntent()).isEqualTo("HOTEL_SEARCH");
        assertThat(result.getCriteria()).isNotNull();
        assertThat(result.getCriteria().getLocationOrHotelName()).isEqualTo("Antalya");
        assertThat(result.getCriteria().getCheckInDate().toString()).isEqualTo("2026-07-15");
        assertThat(result.getCriteria().getCheckOutDate().toString()).isEqualTo("2026-07-20");
        assertThat(result.getCriteria().getAdultCount()).isEqualTo(2);
        assertThat(result.getCriteria().getCurrency()).isEqualTo("EUR");
    }

    @Test
    void extract_shouldThrowException_whenOpenAiReturnsMockModeString() {
        when(geminiExtractionClient.complete(anyString())).thenReturn("[MOCK] Gemini Lite API key is not configured.");

        assertThrows(RuntimeException.class, () -> {
            extractionAgent.extract("Hotel in Antalya", null, null, null);
        });
    }

    @Test
    void extract_shouldThrowException_whenOpenAiReturnsUnparsableJson() {
        when(geminiExtractionClient.complete(anyString())).thenReturn("unparsable response text");

        assertThrows(RuntimeException.class, () -> {
            extractionAgent.extract("Hotel in Antalya", null, null, null);
        });
    }
}
