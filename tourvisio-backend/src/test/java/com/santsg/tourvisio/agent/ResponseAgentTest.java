package com.santsg.tourvisio.agent;

import com.santsg.tourvisio.client.GeminiClient;
import com.santsg.tourvisio.chat.SearchCriteria;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResponseAgentTest {

    @Mock
    private GeminiClient geminiClient;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private ResponseAgent responseAgent;

    @Test
    void clarify_shouldReturnGeminiResponse_whenAvailable() {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setPreferredLanguage("English");
        when(geminiClient.generate(anyString())).thenReturn("Would you like to search for a hotel or a flight?");

        String response = responseAgent.clarify(criteria);

        assertThat(response).isEqualTo("Would you like to search for a hotel or a flight?");
    }

    @Test
    void clarify_shouldReturnFallback_whenGeminiFails() {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setPreferredLanguage("Turkish");
        when(geminiClient.generate(anyString())).thenThrow(new RuntimeException("Gemini error"));
        when(messageSource.getMessage(eq("clarify.intent"), any(), any(Locale.class)))
                .thenReturn("Otel mi aramak istiyorsunuz, yoksa uçak bileti mi?");

        String response = responseAgent.clarify(criteria);

        assertThat(response).isEqualTo("Otel mi aramak istiyorsunuz, yoksa uçak bileti mi?");
    }

    @Test
    void askMissing_shouldReturnGeminiResponse_whenAvailable() {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setPreferredLanguage("English");
        when(geminiClient.generate(anyString())).thenReturn("Please provide the check-in and check-out dates.");

        String response = responseAgent.askMissing(List.of("giriş tarihi", "çıkış tarihi"), criteria);

        assertThat(response).isEqualTo("Please provide the check-in and check-out dates.");
    }

    @Test
    void askMissing_shouldReturnFallback_whenGeminiFails() {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setPreferredLanguage("Turkish");

        when(geminiClient.generate(anyString())).thenReturn("[MOCK] Gemini API is offline");
        when(messageSource.getMessage(eq("field.checkInDate"), any(), any(Locale.class))).thenReturn("giriş tarihi");
        when(messageSource.getMessage(eq("ask.missing.single"), any(), any(Locale.class)))
                .thenReturn("Arama yapabilmem için giriş tarihi bilgisini de belirtir misiniz?");

        String response = responseAgent.askMissing(List.of("giriş tarihi"), criteria);

        assertThat(response).contains("giriş tarihi");
    }

    @Test
    void summarize_shouldReturnGeminiResponse_whenAvailable() {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setPreferredLanguage("English");
        when(geminiClient.generate(anyString())).thenReturn("I found 3 lovely hotels in Antalya.");

        String response = responseAgent.summarize("HOTEL_SEARCH", "[{}]", "Fallback reply", criteria, "Find hotels", 5, 5);

        assertThat(response).isEqualTo("I found 3 lovely hotels in Antalya.");
    }

    @Test
    void summarize_shouldReturnDefaultReply_whenGeminiFailsAndDefaultReplyIsProvided() {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setPreferredLanguage("Turkish");
        when(geminiClient.generate(anyString())).thenThrow(new RuntimeException("Gemini error"));

        String response = responseAgent.summarize("HOTEL_SEARCH", "[{}]", "3 otel bulundu.", criteria, "Otel bul", 3, 3);

        assertThat(response).isEqualTo("3 otel bulundu.");
    }
}
