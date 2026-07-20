package com.santsg.tourvisio.config;

import com.santsg.tourvisio.client.AIFallbackChain;
import com.santsg.tourvisio.client.AIProviderClient;
import com.santsg.tourvisio.client.GeminiClient;
import com.santsg.tourvisio.client.GeminiExtractionClient;
import com.santsg.tourvisio.client.OpenRouterClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Yapay zeka sağlayıcı zincirlerini (Gemini → OpenRouter ücretsiz modeller)
 * kuran konfigürasyon sınıfı.
 *
 * <p>İki ayrı zincir tanımlanır:</p>
 * <ul>
 *   <li>{@code responseAiChain} — kullanıcıya gösterilecek doğal dil cevapları
 *       üretmek için ({@link com.santsg.tourvisio.agent.ResponseAgent} kullanır).
 *       Sıra: Gemini → OpenRouter (OpenAI ücretsiz model) → OpenRouter (yedek ücretsiz model).</li>
 *   <li>{@code extractionAiChain} — kullanıcı mesajından yapılandırılmış arama
 *       kriterleri (JSON) çıkarmak için ({@link com.santsg.tourvisio.agent.ExtractionAgent}
 *       kullanır). Sıra: Gemini Lite → OpenRouter (OpenAI ücretsiz model) → OpenRouter (yedek ücretsiz model).</li>
 * </ul>
 *
 * <p>Her iki OpenRouter modeli de OpenRouter'ın {@code :free} etiketli
 * (ücretsiz) modelleridir — gerçek para harcanmaz, sadece dakikalık/günlük
 * istek limiti vardır.</p>
 */
@Configuration
public class AIProviderConfig {

    @Value("${openrouter.api-key:}")
    private String openRouterApiKey;

    @Value("${openrouter.api-url:https://openrouter.ai/api/v1/chat/completions}")
    private String openRouterApiUrl;

    @Value("${openrouter.model.primary:openai/gpt-oss-20b:free}")
    private String openRouterPrimaryModel;

    @Value("${openrouter.model.backup:meta-llama/llama-3.3-70b-instruct:free}")
    private String openRouterBackupModel;

    private RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }

    @Bean
    public OpenRouterClient openRouterPrimaryClient(RestTemplateBuilder builder) {
        return new OpenRouterClient(restTemplate(builder), openRouterApiKey, openRouterApiUrl,
                openRouterPrimaryModel, "openrouter-openai");
    }

    @Bean
    public OpenRouterClient openRouterBackupClient(RestTemplateBuilder builder) {
        return new OpenRouterClient(restTemplate(builder), openRouterApiKey, openRouterApiUrl,
                openRouterBackupModel, "openrouter-backup");
    }

    /**
     * Sohbet cevapları için sağlayıcı zinciri: Gemini → OpenRouter (OpenAI) → OpenRouter (yedek).
     */
    @Bean
    public AIFallbackChain responseAiChain(GeminiClient geminiClient,
                                            OpenRouterClient openRouterPrimaryClient,
                                            OpenRouterClient openRouterBackupClient) {
        return new AIFallbackChain("response", List.<AIProviderClient>of(
                geminiClient, openRouterPrimaryClient, openRouterBackupClient));
    }

    /**
     * Kriter çıkarma (JSON extraction) için sağlayıcı zinciri: Gemini Lite → OpenRouter (OpenAI) → OpenRouter (yedek).
     */
    @Bean
    public AIFallbackChain extractionAiChain(GeminiExtractionClient geminiExtractionClient,
                                              OpenRouterClient openRouterPrimaryClient,
                                              OpenRouterClient openRouterBackupClient) {
        return new AIFallbackChain("extraction", List.<AIProviderClient>of(
                geminiExtractionClient, openRouterPrimaryClient, openRouterBackupClient));
    }
}
