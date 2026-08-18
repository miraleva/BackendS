package com.santsg.tourvisio.service;

import com.santsg.tourvisio.chat.ChatSessionStore;
import com.santsg.tourvisio.chat.CriteriaMissingFieldsService;
import com.santsg.tourvisio.chat.SearchCriteria;
import com.santsg.tourvisio.chat.SearchCriteriaExtractor;
import com.santsg.tourvisio.chat.SearchCriteriaValidator;
import com.santsg.tourvisio.agent.ExtractionAgent;
import com.santsg.tourvisio.agent.ExtractionResult;
import com.santsg.tourvisio.agent.ResponseAgent;
import com.santsg.tourvisio.service.IntentDetectionService;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
                lenient().when(criteriaValidator.validate(any())).thenReturn(new SearchCriteriaValidator.ValidationResult(true, null));
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

                when(extractionAgent.extract(any(), any(), any(), any(), anyBoolean()))
                                .thenReturn(new ExtractionResult("HOTEL_SEARCH", criteria));

                lenient().when(responseAgent.summarize(any(), any(), any(), any(), any(), anyInt(), anyInt(), any()))
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

                when(extractionAgent.extract(any(), any(), any(), any(), anyBoolean()))
                                .thenReturn(new ExtractionResult("HOTEL_SEARCH", new SearchCriteria()));

                lenient().when(responseAgent.summarize(any(), any(), any(), any(), any(), anyInt(), anyInt(), any()))
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

                when(extractionAgent.extract(any(), any(), any(), any(), anyBoolean()))
                                .thenReturn(new ExtractionResult("HOTEL_SEARCH", new SearchCriteria()));

                lenient().when(responseAgent.summarize(any(), any(), any(), any(), any(), anyInt(), anyInt(), any()))
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

                when(responseAgent.decline(any(), anyBoolean(), any(), any())).thenReturn("This conversation is terminated.");


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

                when(responseAgent.irrelevantWarning(org.mockito.ArgumentMatchers.eq(1), any(), any())).thenReturn("Warning Level 1");
                when(responseAgent.irrelevantWarning(org.mockito.ArgumentMatchers.eq(2), any(), any())).thenReturn("Warning Level 2");
                when(responseAgent.irrelevantWarning(org.mockito.ArgumentMatchers.eq(3), any(), any())).thenReturn("Warning Level 3 Terminated");

                String sessionId = "session-irrelevant-test";

                ChatResponse r1 = service.orchestrate(ChatRequest.builder().message("asdljk").sessionId(sessionId).build());
                assertThat(r1.getChatStatus()).isEqualTo("ACTIVE");
                assertThat(r1.getReply()).isEqualTo("Warning Level 1");

                ChatResponse r2 = service.orchestrate(ChatRequest.builder().message("???????").sessionId(sessionId).build());
                assertThat(r2.getChatStatus()).isEqualTo("ACTIVE");
                assertThat(r2.getReply()).isEqualTo("Warning Level 2");

                ChatResponse r3 = service.orchestrate(ChatRequest.builder().message("zzzzxx").sessionId(sessionId).build());
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

        @Test
        void orchestrate_shouldProbeFlexibleDatesAndApplyDefaults() {
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
                criteria.setFlexibleDates(true);
                criteria.setStayNights(4);
                criteria.setCurrency("TRY");

                when(extractionAgent.extract(any(), any(), any(), any(), anyBoolean()))
                                .thenReturn(new ExtractionResult("HOTEL_SEARCH", criteria));


                com.santsg.tourvisio.dto.HotelSearchResponseItem sampleItem = new com.santsg.tourvisio.dto.HotelSearchResponseItem();
                sampleItem.setName("Akra Hotel");
                sampleItem.setPrice(1000.0);
                lenient().when(hotelSearchService.searchHotelsRaw(any())).thenReturn(List.of(sampleItem));

                when(hotelSearchService.searchFromCriteria(any())).thenReturn(ChatSearchResponse.builder()
                                .reply("Found flexible hotels")
                                .searchType("HOTEL_SEARCH")
                                .success(true)
                                .results(List.of(sampleItem))
                                .build());

                lenient().when(responseAgent.summarize(any(), any(), any(), any(), any(), anyInt(), anyInt(), any()))
                                .thenReturn("Found flexible hotels for Antalya");

                ChatResponse response = service.orchestrate(ChatRequest.builder()
                                .message("Antalya'da en yakın tarihlerde 4 gece otel")
                                .sessionId("session-flexible-test")
                                .build());

                assertThat(response).isNotNull();
                assertThat(response.getSuccess()).isTrue();
                assertThat(response.getSearchType()).isEqualTo("HOTEL_SEARCH");

                SearchCriteria savedCriteria = sessionStore.getOrCreate("session-flexible-test");
                assertThat(savedCriteria.getFlexibleDates()).isTrue();
                assertThat(savedCriteria.getAdultCount()).isEqualTo(1);
                assertThat(savedCriteria.getAssumedGuestCount()).isTrue();
                assertThat(savedCriteria.getCheckInDate()).isNotNull();
                assertThat(savedCriteria.getCheckOutDate()).isNotNull();
                assertThat(savedCriteria.getCheckOutDate()).isEqualTo(savedCriteria.getCheckInDate().plusDays(4));
        }

        @Test
        void orchestrate_shouldProbeFlexibleDatesAndApplyDefaultsForFlightSearch() {
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
                criteria.setDepartureLocation("Istanbul");
                criteria.setArrivalLocation("Antalya");
                criteria.setFlexibleDates(true);
                criteria.setCurrency("TRY");

                when(extractionAgent.extract(any(), any(), any(), any(), anyBoolean()))
                                .thenReturn(new ExtractionResult("FLIGHT_SEARCH", criteria));


                com.santsg.tourvisio.dto.FlightSearchResponseItem flightItem = com.santsg.tourvisio.dto.FlightSearchResponseItem.builder()
                                .airline("THY")
                                .price(1500.0)
                                .build();

                when(flightSearchService.searchFromCriteria(any())).thenReturn(ChatSearchResponse.builder()
                                .reply("Found flexible flights")
                                .searchType("FLIGHT_SEARCH")
                                .success(true)
                                .results(List.of(flightItem))
                                .build());

                lenient().when(responseAgent.summarize(any(), any(), any(), any(), any(), anyInt(), anyInt(), any()))
                                .thenReturn("Found flexible flights from Istanbul to Antalya");

                ChatResponse response = service.orchestrate(ChatRequest.builder()
                                .message("Istanbul'dan Antalya'ya en yakın tarihte uçak")
                                .sessionId("session-flight-flexible-test")
                                .build());

                assertThat(response).isNotNull();
                assertThat(response.getSuccess()).isTrue();
                assertThat(response.getSearchType()).isEqualTo("FLIGHT_SEARCH");

                SearchCriteria savedCriteria = sessionStore.getOrCreate("session-flight-flexible-test");
                assertThat(savedCriteria.getFlexibleDates()).isTrue();
                assertThat(savedCriteria.getPassengerCount()).isEqualTo(1);
                assertThat(savedCriteria.getAssumedPassengerCount()).isTrue();
                assertThat(savedCriteria.getTripType()).isEqualTo("ONE_WAY");
                assertThat(savedCriteria.getAssumedTripType()).isTrue();
                assertThat(savedCriteria.getDepartureDate()).isNotNull();
        }

        @Test
        void orchestrate_shouldSwitchIntentFromHotelToFlightSearchAndTransferCriteria() {
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

                String sessionId = "session-intent-switch-test";
                SearchCriteria initialHotelCriteria = new SearchCriteria();
                initialHotelCriteria.setSearchType("HOTEL_SEARCH");
                initialHotelCriteria.setLocationOrHotelName("Antalya");
                initialHotelCriteria.setCheckInDate(java.time.LocalDate.of(2026, 8, 15));
                initialHotelCriteria.setCheckOutDate(java.time.LocalDate.of(2026, 8, 20));
                initialHotelCriteria.setAdultCount(2);
                initialHotelCriteria.setCurrency("TRY");
                sessionStore.save(sessionId, initialHotelCriteria);


                SearchCriteria incomingFlightCriteria = new SearchCriteria();
                incomingFlightCriteria.setSearchType("FLIGHT_SEARCH");
                incomingFlightCriteria.setDepartureLocation("Istanbul");

                when(extractionAgent.extract(any(), any(), any(), any(), anyBoolean()))
                                .thenReturn(new ExtractionResult("FLIGHT_SEARCH", incomingFlightCriteria));


                com.santsg.tourvisio.dto.FlightSearchResponseItem flightItem = com.santsg.tourvisio.dto.FlightSearchResponseItem.builder()
                                .airline("Pegasus")
                                .price(1200.0)
                                .build();

                when(flightSearchService.searchFromCriteria(any())).thenReturn(ChatSearchResponse.builder()
                                .reply("Found flights for Antalya")
                                .searchType("FLIGHT_SEARCH")
                                .success(true)
                                .results(List.of(flightItem))
                                .build());

                lenient().when(responseAgent.summarize(any(), any(), any(), any(), any(), anyInt(), anyInt(), any()))
                                .thenReturn("Found flight options for Antalya");

                ChatResponse response = service.orchestrate(ChatRequest.builder()
                                .message("ilk uçağı listele")
                                .sessionId(sessionId)
                                .build());

                assertThat(response).isNotNull();
                assertThat(response.getSuccess()).isTrue();
                assertThat(response.getSearchType()).isEqualTo("FLIGHT_SEARCH");

                SearchCriteria updatedCriteria = sessionStore.getOrCreate(sessionId);
                assertThat(updatedCriteria.getSearchType()).isEqualTo("FLIGHT_SEARCH");
                assertThat(updatedCriteria.getArrivalLocation()).isEqualTo("Antalya");
                assertThat(updatedCriteria.getDepartureDate()).isEqualTo(java.time.LocalDate.of(2026, 8, 15));
                assertThat(updatedCriteria.getReturnDate()).isEqualTo(java.time.LocalDate.of(2026, 8, 20));
                assertThat(updatedCriteria.getPassengerCount()).isEqualTo(2);
                assertThat(updatedCriteria.getLocationOrHotelName()).isNull();
        }

        @Test
        void orchestrate_shouldPreserveChildAndInfantCounts() {
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

                String sessionId = "child-infant-test-session";
                SearchCriteria extracted = new SearchCriteria();
                extracted.setAdultCount(2);
                extracted.setChildCount(1);
                extracted.setInfantCount(1);

                when(extractionAgent.extract(any(), any(), any(), any(), anyBoolean()))
                                .thenReturn(new ExtractionResult("HOTEL_SEARCH", extracted));

                service.orchestrate(ChatRequest.builder()
                                .message("2 yetişkin 1 çocuk 1 bebek")
                                .sessionId(sessionId)
                                .build());

                SearchCriteria saved = sessionStore.getOrCreate(sessionId);
                assertThat(saved.getAdultCount()).isEqualTo(2);
                assertThat(saved.getChildCount()).isEqualTo(1);
                assertThat(saved.getInfantCount()).isEqualTo(1);
        }

        @Test
        void orchestrate_shouldParseInfantCountWhenKeywordPrecedesNumber() {
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

                String sessionId = "bebek-2-tane-test-session";
                SearchCriteria initial = new SearchCriteria();
                initial.setSearchType("HOTEL_SEARCH");
                initial.setAdultCount(2);
                sessionStore.save(sessionId, initial);

                SearchCriteria extracted = new SearchCriteria();
                extracted.setSearchType("HOTEL_SEARCH");
                when(extractionAgent.extract(any(), any(), any(), any(), anyBoolean()))
                                .thenReturn(new ExtractionResult("HOTEL_SEARCH", extracted));

                service.orchestrate(ChatRequest.builder()
                                .message("bebek 2 tane olucak")
                                .sessionId(sessionId)
                                .build());

                SearchCriteria saved = sessionStore.getOrCreate(sessionId);
                assertThat(saved.getInfantCount()).isEqualTo(2);
        }

        @Test
        void orchestrate_shouldHandleIncrementalChildAddition() {
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

                String sessionId = "incremental-child-test-session";
                SearchCriteria initial = new SearchCriteria();
                initial.setSearchType("HOTEL_SEARCH");
                initial.setAdultCount(2);
                initial.setChildCount(2);
                initial.setChildAges(java.util.List.of(5, 8));
                sessionStore.save(sessionId, initial);

                SearchCriteria extracted = new SearchCriteria();
                extracted.setSearchType("HOTEL_SEARCH");
                when(extractionAgent.extract(any(), any(), any(), any(), anyBoolean()))
                                .thenReturn(new ExtractionResult("HOTEL_SEARCH", extracted));

                service.orchestrate(ChatRequest.builder()
                                .message("1 çocuk daha var")
                                .sessionId(sessionId)
                                .build());

                SearchCriteria saved = sessionStore.getOrCreate(sessionId);
                assertThat(saved.getChildCount()).isEqualTo(3);
                assertThat(saved.getChildAges()).containsExactly(5, 8);
        }
}




