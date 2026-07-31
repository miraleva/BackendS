package com.santsg.tourvisio.service;

import com.santsg.tourvisio.chat.ChatSessionStore;
import com.santsg.tourvisio.chat.CriteriaMissingFieldsService;
import com.santsg.tourvisio.chat.SearchCriteria;
import com.santsg.tourvisio.chat.SearchCriteriaExtractor;
import com.santsg.tourvisio.chat.SearchCriteriaValidator;
import com.santsg.tourvisio.agent.ExtractionAgent;
import com.santsg.tourvisio.agent.ExtractionResult;
import com.santsg.tourvisio.agent.ResponseAgent;
import com.santsg.tourvisio.dto.ChatRequest;
import com.santsg.tourvisio.dto.ChatResponse;
import com.santsg.tourvisio.dto.ChatSearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
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

        @Mock
        private SearchCriteriaValidator criteriaValidator;

        @InjectMocks
        private ChatOrchestrationService orchestrationService;

        @BeforeEach
        void setUp() {
                lenient().when(criteriaValidator.validate(any()))
                                .thenReturn(new SearchCriteriaValidator.ValidationResult(true, null));
        }

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
                                missingFieldsService, criteriaValidator,
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

                when(extractionAgent.extract(any(), any(), any(), any()))
                                .thenReturn(new ExtractionResult("HOTEL_SEARCH", criteria));

                when(responseAgent.summarize(any(), any(), any(), any(), any(), anyInt(), anyInt()))
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
        void orchestrate_shouldKeepHotelResultsWhenCombinedFlightSearchFails() {
                ChatSessionManager chatSessionManager = new ChatSessionManager();
                ChatSessionStore sessionStore = new ChatSessionStore();
                SearchCriteriaExtractor extractor = new SearchCriteriaExtractor();
                CriteriaMissingFieldsService missingFieldsService = new CriteriaMissingFieldsService();

                ChatOrchestrationService service = new ChatOrchestrationService(
                                intentDetectionService,
                                chatSessionManager,
                                sessionStore,
                                extractor,
                                missingFieldsService, criteriaValidator,
                                extractionAgent,
                                responseAgent,
                                hotelSearchService,
                                flightSearchService);

                SearchCriteria criteria = new SearchCriteria();
                criteria.setLocationOrHotelName("Antalya");
                criteria.setDepartureLocation("Istanbul");
                criteria.setArrivalLocation("Antalya");
                criteria.setCheckInDate(java.time.LocalDate.of(2026, 8, 15));
                criteria.setCheckOutDate(java.time.LocalDate.of(2026, 8, 20));
                criteria.setDepartureDate(java.time.LocalDate.of(2026, 8, 15));
                criteria.setReturnDate(java.time.LocalDate.of(2026, 8, 20));
                criteria.setAdultCount(2);
                criteria.setPassengerCount(2);
                criteria.setChildCount(0);
                criteria.setRoomCount(1);
                criteria.setCurrency("EUR");
                criteria.setTripType("ROUND_TRIP");

                when(extractionAgent.extract(any(), any(), any(), any()))
                                .thenReturn(new ExtractionResult("COMBINED_SEARCH", criteria));
                when(hotelSearchService.searchFromCriteria(any())).thenReturn(ChatSearchResponse.builder()
                                .reply("Hotels found")
                                .searchType("HOTEL_SEARCH")
                                .success(true)
                                .results(List.of("Hotel sample"))
                                .build());
                when(flightSearchService.searchFromCriteria(any()))
                                .thenThrow(new RuntimeException("flight provider unavailable"));
                when(responseAgent.summarize(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                                .thenReturn("Hotel result is still available");

                ChatResponse response = service.orchestrate(ChatRequest.builder()
                                .message("Antalya için otel ve uçak istiyorum")
                                .sessionId("combined-session-test")
                                .build());

                assertThat(response.getSearchType()).isEqualTo("COMBINED_SEARCH");
                assertThat(response.getSuccess()).isTrue();
                assertThat(response.getResults()).hasSize(1);
                assertThat(response.getResults().getFirst()).isEqualTo("Hotel sample");
                verify(hotelSearchService).searchFromCriteria(any());
                verify(flightSearchService).searchFromCriteria(any());
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
                                missingFieldsService, criteriaValidator,
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
                                missingFieldsService, criteriaValidator,
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

                when(extractionAgent.extract(any(), any(), any(), any()))
                                .thenReturn(new ExtractionResult("HOTEL_SEARCH", new SearchCriteria()));

                when(responseAgent.summarize(any(), any(), any(), any(), any(), anyInt(), anyInt()))
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
                                missingFieldsService, criteriaValidator,
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

                when(extractionAgent.extract(any(), any(), any(), any()))
                                .thenReturn(new ExtractionResult("HOTEL_SEARCH", new SearchCriteria()));

                when(responseAgent.summarize(any(), any(), any(), any(), any(), anyInt(), anyInt()))
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

        @Test
        void orchestrate_shouldImmediatelyTerminateOnProfanity() {
                ChatSessionManager chatSessionManager = new ChatSessionManager();
                ChatSessionStore sessionStore = new ChatSessionStore();
                SearchCriteriaExtractor extractor = new SearchCriteriaExtractor();
                CriteriaMissingFieldsService missingFieldsService = new CriteriaMissingFieldsService();

                ChatOrchestrationService service = new ChatOrchestrationService(
                                intentDetectionService,
                                chatSessionManager,
                                sessionStore,
                                extractor,
                                missingFieldsService, criteriaValidator,
                                extractionAgent,
                                responseAgent,
                                hotelSearchService,
                                flightSearchService);

                when(responseAgent.profanityTerminated(any(), any())).thenReturn("Profanity message terminated.");

                ChatResponse response = service.orchestrate(ChatRequest.builder()
                                .message("amk bu ne")
                                .sessionId("session-profanity-test")
                                .build());

                assertThat(response.getChatStatus()).isEqualTo("TERMINATED");
                assertThat(response.getSearchType()).isEqualTo("PROFANITY");
                assertThat(response.getReply()).isEqualTo("Profanity message terminated.");
        }

        @Test
        void orchestrate_shouldLockSessionWhenTerminated() {
                ChatSessionManager chatSessionManager = new ChatSessionManager();
                ChatSessionStore sessionStore = new ChatSessionStore();
                SearchCriteriaExtractor extractor = new SearchCriteriaExtractor();
                CriteriaMissingFieldsService missingFieldsService = new CriteriaMissingFieldsService();

                ChatOrchestrationService service = new ChatOrchestrationService(
                                intentDetectionService,
                                chatSessionManager,
                                sessionStore,
                                extractor,
                                missingFieldsService, criteriaValidator,
                                extractionAgent,
                                responseAgent,
                                hotelSearchService,
                                flightSearchService);

                String sessionId = "session-terminated-lock-test";
                ChatSessionManager.SessionState state = chatSessionManager.getOrCreateSession(sessionId);
                state.setChatStatus("TERMINATED");

                when(responseAgent.decline(any(), anyBoolean(), any())).thenReturn("This conversation is terminated.");

                ChatResponse response = service.orchestrate(ChatRequest.builder()
                                .message("Antalya hotel July 15")
                                .sessionId(sessionId)
                                .build());

                assertThat(response.getChatStatus()).isEqualTo("TERMINATED");
                assertThat(response.getSearchType()).isEqualTo("OUT_OF_SCOPE");
                assertThat(response.getReply()).isEqualTo("This conversation is terminated.");
        }

        @Test
        void orchestrate_shouldProgressivelyWarnOnIrrelevantMessagesAndTerminateOnThird() {
                ChatSessionManager chatSessionManager = new ChatSessionManager();
                ChatSessionStore sessionStore = new ChatSessionStore();
                SearchCriteriaExtractor extractor = new SearchCriteriaExtractor();
                CriteriaMissingFieldsService missingFieldsService = new CriteriaMissingFieldsService();

                ChatOrchestrationService service = new ChatOrchestrationService(
                                intentDetectionService,
                                chatSessionManager,
                                sessionStore,
                                extractor,
                                missingFieldsService, criteriaValidator,
                                extractionAgent,
                                responseAgent,
                                hotelSearchService,
                                flightSearchService);

                when(extractionAgent.extract(any(), any(), any(), any(), anyBoolean()))
                                .thenReturn(new ExtractionResult("IRRELEVANT", new SearchCriteria()));

                when(responseAgent.irrelevantWarning(org.mockito.ArgumentMatchers.eq(1), any(), any()))
                                .thenReturn("Warning Level 1");
                when(responseAgent.irrelevantWarning(org.mockito.ArgumentMatchers.eq(2), any(), any()))
                                .thenReturn("Warning Level 2");
                when(responseAgent.irrelevantWarning(org.mockito.ArgumentMatchers.eq(3), any(), any()))
                                .thenReturn("Warning Level 3 Terminated");

                String sessionId = "session-irrelevant-test";

                ChatResponse r1 = service
                                .orchestrate(ChatRequest.builder().message("asdljk").sessionId(sessionId).build());
                assertThat(r1.getChatStatus()).isEqualTo("ACTIVE");
                assertThat(r1.getReply()).isEqualTo("Warning Level 1");

                ChatResponse r2 = service
                                .orchestrate(ChatRequest.builder().message("???????").sessionId(sessionId).build());
                assertThat(r2.getChatStatus()).isEqualTo("ACTIVE");
                assertThat(r2.getReply()).isEqualTo("Warning Level 2");

                ChatResponse r3 = service
                                .orchestrate(ChatRequest.builder().message("zzzzxx").sessionId(sessionId).build());
                assertThat(r3.getChatStatus()).isEqualTo("TERMINATED");
                assertThat(r3.getReply()).isEqualTo("Warning Level 3 Terminated");
        }

        @Test
        void orchestrate_shouldDetectEnglishOnFirstMessageStartingWithCapitalI() {
                ChatSessionManager chatSessionManager = new ChatSessionManager();
                ChatSessionStore sessionStore = new ChatSessionStore();
                SearchCriteriaExtractor extractor = new SearchCriteriaExtractor();
                CriteriaMissingFieldsService missingFieldsService = new CriteriaMissingFieldsService();

                ChatOrchestrationService service = new ChatOrchestrationService(
                                intentDetectionService,
                                chatSessionManager,
                                sessionStore,
                                extractor,
                                missingFieldsService, criteriaValidator,
                                extractionAgent,
                                responseAgent,
                                hotelSearchService,
                                flightSearchService);

                SearchCriteria criteria1 = new SearchCriteria();
                criteria1.setLocationOrHotelName("Antalya");
                when(extractionAgent.extract(any(), any(), any(), any(), anyBoolean()))
                                .thenReturn(new ExtractionResult("HOTEL_SEARCH", criteria1));

                lenient().when(hotelSearchService.searchFromCriteria(any())).thenReturn(ChatSearchResponse.builder().reply("Found hotels").success(true).build());
                lenient().when(responseAgent.askMissing(any(), any(), any())).thenReturn("Could you please share check-in date?");

                service.orchestrate(ChatRequest.builder()
                                .message("I want a hotel in Antalya")
                                .country("Turkey")
                                .build());

                SearchCriteria savedCriteria1 = sessionStore.getOrCreate(sessionStore.getStoreMap().keySet().iterator().next());
                assertThat(savedCriteria1.getPreferredLanguage()).isEqualTo("English");
        }

        @Test
        void orchestrate_shouldDetectEnglishOnFirstMessageStartingWithIs() {
                ChatSessionManager chatSessionManager = new ChatSessionManager();
                ChatSessionStore sessionStore = new ChatSessionStore();
                SearchCriteriaExtractor extractor = new SearchCriteriaExtractor();
                CriteriaMissingFieldsService missingFieldsService = new CriteriaMissingFieldsService();

                ChatOrchestrationService service = new ChatOrchestrationService(
                                intentDetectionService,
                                chatSessionManager,
                                sessionStore,
                                extractor,
                                missingFieldsService, criteriaValidator,
                                extractionAgent,
                                responseAgent,
                                hotelSearchService,
                                flightSearchService);

                SearchCriteria criteria2 = new SearchCriteria();
                when(extractionAgent.extract(any(), any(), any(), any(), anyBoolean()))
                                .thenReturn(new ExtractionResult("HOTEL_SEARCH", criteria2));

                lenient().when(hotelSearchService.searchFromCriteria(any())).thenReturn(ChatSearchResponse.builder().reply("Found hotels").success(true).build());
                lenient().when(responseAgent.askMissing(any(), any(), any())).thenReturn("Where would you like to stay?");

                service.orchestrate(ChatRequest.builder()
                                .message("Is there a hotel available?")
                                .country("Turkey")
                                .build());

                SearchCriteria savedCriteria2 = sessionStore.getOrCreate(sessionStore.getStoreMap().keySet().iterator().next());
                assertThat(savedCriteria2.getPreferredLanguage()).isEqualTo("English");
        }

        @Test
        void orchestrate_shouldFallbackToCountryOnNeutralFirstMessage() {
                ChatSessionManager chatSessionManager = new ChatSessionManager();
                ChatSessionStore sessionStore = new ChatSessionStore();
                SearchCriteriaExtractor extractor = new SearchCriteriaExtractor();
                CriteriaMissingFieldsService missingFieldsService = new CriteriaMissingFieldsService();

                ChatOrchestrationService service = new ChatOrchestrationService(
                                intentDetectionService,
                                chatSessionManager,
                                sessionStore,
                                extractor,
                                missingFieldsService, criteriaValidator,
                                extractionAgent,
                                responseAgent,
                                hotelSearchService,
                                flightSearchService);

                SearchCriteria criteria3 = new SearchCriteria();
                criteria3.setCheckInDate(java.time.LocalDate.of(2026, 8, 3));
                when(extractionAgent.extract(any(), any(), any(), any(), anyBoolean()))
                                .thenReturn(new ExtractionResult("HOTEL_SEARCH", criteria3));

                lenient().when(hotelSearchService.searchFromCriteria(any())).thenReturn(ChatSearchResponse.builder().reply("Found hotels").success(true).build());
                lenient().when(responseAgent.askMissing(any(), any(), any())).thenReturn("Hangi şehirde kalmak istersiniz?");

                service.orchestrate(ChatRequest.builder()
                                .message("03/08/2026")
                                .country("Turkey")
                                .build());

                SearchCriteria savedCriteria3 = sessionStore.getOrCreate(sessionStore.getStoreMap().keySet().iterator().next());
                assertThat(savedCriteria3.getPreferredLanguage()).isEqualTo("Turkey");
                assertThat(com.santsg.tourvisio.util.LocaleResolver.resolveLanguageName(savedCriteria3)).isEqualTo("Turkish");
        }
}
