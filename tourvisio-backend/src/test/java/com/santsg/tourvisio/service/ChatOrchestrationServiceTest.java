package com.santsg.tourvisio.service;

import com.santsg.tourvisio.chat.ChatSessionStore;
import com.santsg.tourvisio.chat.CriteriaMissingFieldsService;
import com.santsg.tourvisio.chat.SearchCriteriaExtractor;
import com.santsg.tourvisio.client.AIProviderClient;
import com.santsg.tourvisio.dto.ChatRequest;
import com.santsg.tourvisio.dto.ChatResponse;
import com.santsg.tourvisio.dto.ChatSearchResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatOrchestrationServiceTest {

        @Mock
        private IntentDetectionService intentDetectionService;

        @Mock
        private AIProviderClient aiProviderClient;

        @Mock
        private HotelSearchService hotelSearchService;

        @Mock
        private FlightSearchService flightSearchService;

        @InjectMocks
        private ChatOrchestrationService orchestrationService;

        @Test
        void orchestrate_shouldUseHotelSearchServiceWhenCriteriaAreComplete() {
                ChatSessionManager chatSessionManager = new ChatSessionManager();
                ChatSessionStore sessionStore = new ChatSessionStore();
                SearchCriteriaExtractor extractor = new SearchCriteriaExtractor(aiProviderClient);
                CriteriaMissingFieldsService missingFieldsService = new CriteriaMissingFieldsService(aiProviderClient);

                ChatOrchestrationService service = new ChatOrchestrationService(
                                intentDetectionService,
                                chatSessionManager,
                                sessionStore,
                                extractor,
                                missingFieldsService,
                                aiProviderClient,
                                hotelSearchService,
                                flightSearchService);

                when(intentDetectionService
                                .detectIntent("Hotel in Antalya from July 15 to July 20 for 2 adults in EUR"))
                                .thenReturn("HOTEL_SEARCH");
                when(aiProviderClient.complete(any())).thenReturn("""
                                {
                                  "locationOrHotelName": "Antalya",
                                  "checkInDate": "2026-07-15",
                                  "checkOutDate": "2026-07-20",
                                  "adultCount": 2,
                                  "currency": "EUR"
                                }
                                """);
                when(hotelSearchService.searchFromCriteria(any())).thenReturn(ChatSearchResponse.builder()
                                .reply("Found suitable hotels for Antalya")
                                .searchType("HOTEL_SEARCH")
                                .success(true)
                                .results(List.of("Hotel sample"))
                                .build());

                ChatResponse response = service.orchestrate(ChatRequest.builder()
                                .message("Hotel in Antalya from July 15 to July 20 for 2 adults in EUR")
                                .sessionId("session-test")
                                .build());

                assertThat(response.getReply()).contains("Antalya");
                assertThat(response.getSearchType()).isEqualTo("HOTEL_SEARCH");
                assertThat(response.getSuccess()).isTrue();
                assertThat(response.getResults()).hasSize(1);
                verify(hotelSearchService).searchFromCriteria(any());
        }
}
