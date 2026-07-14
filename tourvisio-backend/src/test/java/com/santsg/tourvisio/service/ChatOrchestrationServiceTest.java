package com.santsg.tourvisio.service;

import com.santsg.tourvisio.chat.ChatSessionStore;
import com.santsg.tourvisio.chat.CriteriaMissingFieldsService;
import com.santsg.tourvisio.chat.SearchCriteria;
import com.santsg.tourvisio.chat.SearchCriteriaExtractor;
import com.santsg.tourvisio.agent.ExtractionAgent;
import com.santsg.tourvisio.agent.ExtractionResult;
import com.santsg.tourvisio.agent.ResponseAgent;
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
        private ExtractionAgent extractionAgent;

        @Mock
        private ResponseAgent responseAgent;

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
                SearchCriteriaExtractor extractor = new SearchCriteriaExtractor();
                CriteriaMissingFieldsService missingFieldsService = new CriteriaMissingFieldsService();

                ChatOrchestrationService service = new ChatOrchestrationService(
                                intentDetectionService,
                                chatSessionManager,
                                sessionStore,
                                extractor,
                                missingFieldsService,
                                extractionAgent,
                                responseAgent,
                                hotelSearchService,
                                flightSearchService);

                SearchCriteria criteria = new SearchCriteria();
                criteria.setLocationOrHotelName("Antalya");
                criteria.setCheckInDate(java.time.LocalDate.of(2026, 7, 15));
                criteria.setCheckOutDate(java.time.LocalDate.of(2026, 7, 20));
                criteria.setAdultCount(2);
                criteria.setCurrency("EUR");

                when(extractionAgent.extract(any(), any()))
                                .thenReturn(new ExtractionResult("HOTEL_SEARCH", criteria));

                when(responseAgent.summarize(any(), any(), any(), any()))
                                .thenReturn("Found suitable hotels for Antalya");

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

        @Test
        void orchestrate_shouldThrowForbiddenWhenSessionBelongsToAnotherUser() {
                ChatSessionManager chatSessionManager = new ChatSessionManager();
                ChatSessionStore sessionStore = new ChatSessionStore();
                SearchCriteriaExtractor extractor = new SearchCriteriaExtractor();
                CriteriaMissingFieldsService missingFieldsService = new CriteriaMissingFieldsService();

                ChatOrchestrationService service = new ChatOrchestrationService(
                                intentDetectionService,
                                chatSessionManager,
                                sessionStore,
                                extractor,
                                missingFieldsService,
                                extractionAgent,
                                responseAgent,
                                hotelSearchService,
                                flightSearchService);

                chatSessionManager.getOrCreateSession("session-forbidden-test", 123L);

                org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
                        service.orchestrate(ChatRequest.builder()
                                        .message("Hello")
                                        .sessionId("session-forbidden-test")
                                        .build(), 456L);
                }).isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                  .hasMessageContaining("Access denied to session");
        }

        @Test
        void orchestrate_shouldAssignDirectlyWhenAskedForAdultCount() {
                ChatSessionManager chatSessionManager = new ChatSessionManager();
                ChatSessionStore sessionStore = new ChatSessionStore();
                SearchCriteriaExtractor extractor = new SearchCriteriaExtractor();
                CriteriaMissingFieldsService missingFieldsService = new CriteriaMissingFieldsService();

                ChatOrchestrationService service = new ChatOrchestrationService(
                                intentDetectionService,
                                chatSessionManager,
                                sessionStore,
                                extractor,
                                missingFieldsService,
                                extractionAgent,
                                responseAgent,
                                hotelSearchService,
                                flightSearchService);

                String sessionId = "session-assign-test";
                ChatSessionManager.SessionState sessionState = chatSessionManager.getOrCreateSession(sessionId);
                sessionState.setLastRequestedField("yetişkin sayısı");

                SearchCriteria criteria = sessionStore.getOrCreate(sessionId);
                criteria.setSearchType("HOTEL_SEARCH");
                criteria.setLocationOrHotelName("Antalya");
                criteria.setCheckInDate(java.time.LocalDate.of(2026, 7, 15));
                criteria.setCheckOutDate(java.time.LocalDate.of(2026, 7, 20));
                criteria.setCurrency("EUR");

                when(extractionAgent.extract(any(), any()))
                                .thenReturn(new ExtractionResult("HOTEL_SEARCH", new SearchCriteria()));

                when(responseAgent.summarize(any(), any(), any(), any()))
                                .thenReturn("Found suitable hotels");

                when(hotelSearchService.searchFromCriteria(any())).thenReturn(ChatSearchResponse.builder()
                                .reply("Found suitable hotels")
                                .searchType("HOTEL_SEARCH")
                                .success(true)
                                .results(List.of("Hotel sample"))
                                .build());

                ChatResponse response = service.orchestrate(ChatRequest.builder()
                                .message("2")
                                .sessionId(sessionId)
                                .build());

                assertThat(criteria.getAdultCount()).isEqualTo(2);
                assertThat(response.getSuccess()).isTrue();
        }

        @Test
        void orchestrate_shouldAssignDirectlyWhenAskedForCheckOutDate() {
                ChatSessionManager chatSessionManager = new ChatSessionManager();
                ChatSessionStore sessionStore = new ChatSessionStore();
                SearchCriteriaExtractor extractor = new SearchCriteriaExtractor();
                CriteriaMissingFieldsService missingFieldsService = new CriteriaMissingFieldsService();

                ChatOrchestrationService service = new ChatOrchestrationService(
                                intentDetectionService,
                                chatSessionManager,
                                sessionStore,
                                extractor,
                                missingFieldsService,
                                extractionAgent,
                                responseAgent,
                                hotelSearchService,
                                flightSearchService);

                String sessionId = "session-date-test";
                ChatSessionManager.SessionState sessionState = chatSessionManager.getOrCreateSession(sessionId);
                sessionState.setLastRequestedField("çıkış tarihi");

                SearchCriteria criteria = sessionStore.getOrCreate(sessionId);
                criteria.setSearchType("HOTEL_SEARCH");
                criteria.setLocationOrHotelName("Antalya");
                criteria.setCheckInDate(java.time.LocalDate.of(2026, 7, 15));
                criteria.setAdultCount(2);
                criteria.setCurrency("EUR");

                when(extractionAgent.extract(any(), any()))
                                .thenReturn(new ExtractionResult("HOTEL_SEARCH", new SearchCriteria()));

                when(responseAgent.summarize(any(), any(), any(), any()))
                                .thenReturn("Found suitable hotels");

                when(hotelSearchService.searchFromCriteria(any())).thenReturn(ChatSearchResponse.builder()
                                .reply("Found suitable hotels")
                                .searchType("HOTEL_SEARCH")
                                .success(true)
                                .results(List.of("Hotel sample"))
                                .build());

                ChatResponse response = service.orchestrate(ChatRequest.builder()
                                .message("23 Temmuz")
                                .sessionId(sessionId)
                                .build());

                assertThat(criteria.getCheckOutDate()).isEqualTo(java.time.LocalDate.of(2026, 7, 23));
                assertThat(response.getSuccess()).isTrue();
        }
}
