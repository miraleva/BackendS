package com.santsg.tourvisio.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Birden fazla {@link AIProviderClient}'ı sırayla dener: ilki geçerli bir
 * yanıt döndürmezse (boş, hata, ya da "[MOCK]" ile başlayan bir yanıt) bir
 * sonrakine geçer. Böylece örn. Gemini kota/hata verdiğinde otomatik olarak
 * ücretsiz bir yedek modele (OpenRouter üzerinden) düşülür.
 *
 * <p>Tüm sağlayıcılar başarısız olursa, son denenen sağlayıcının (geçersiz)
 * yanıtı olduğu gibi döner — böylece çağıran taraf (ör. {@code isValidResponse}
 * kontrolleri) mevcut "[MOCK]" tabanlı hata algılama mantığını değiştirmeden
 * kullanmaya devam edebilir.</p>
 */
public class AIFallbackChain implements AIProviderClient {

    private static final Logger log = LoggerFactory.getLogger(AIFallbackChain.class);

    private final List<AIProviderClient> providers;
    private final String chainLabel;

    public AIFallbackChain(String chainLabel, List<AIProviderClient> providers) {
        if (providers == null || providers.isEmpty()) {
            throw new IllegalArgumentException("AIFallbackChain en az bir provider gerektirir.");
        }
        this.chainLabel = chainLabel;
        this.providers = providers;
    }

    @Override
    public String complete(String prompt) {
        String lastResponse = null;

        for (int i = 0; i < providers.size(); i++) {
            AIProviderClient provider = providers.get(i);
            String response = provider.complete(prompt);
            lastResponse = response;

            if (isValid(response)) {
                if (i > 0) {
                    log.info("[AIFallbackChain:{}] '{}' sağlayıcısına düşüldü (önceki {} sağlayıcı başarısız).",
                            chainLabel, provider.providerName(), i);
                }
                logLlmRequest(provider.providerName(), prompt, response);
                return response;
            }

            logLlmRequest(provider.providerName() + " (FAILED)", prompt, response);
            log.warn("[AIFallbackChain:{}] '{}' sağlayıcısı geçersiz yanıt döndürdü, bir sonrakine geçiliyor.",
                    chainLabel, provider.providerName());
        }

        log.error("[AIFallbackChain:{}] Tüm sağlayıcılar ({} adet) başarısız oldu.", chainLabel, providers.size());
        logLlmRequest(chainLabel + " (ALL FAILED)", prompt, lastResponse);
        return lastResponse;
    }

    private void logLlmRequest(String providerName, String prompt, String response) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        StackTraceElement caller = null;
        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();
            if (className.contains("Thread") || 
                className.contains("java.lang") || 
                className.contains("sun.reflect") || 
                className.contains("jdk.internal") || 
                className.contains("org.mockito") || 
                className.contains("AIProviderClient") || 
                className.contains("AIFallbackChain") ||
                className.equals(this.getClass().getName())) {
                continue;
            }
            caller = element;
            break;
        }

        System.out.println("==================================================");
        System.out.println("[LLM REQUEST DEBUG]");
        if (caller != null) {
            System.out.println("Source File: " + caller.getFileName());
            System.out.println("Line Number: " + caller.getLineNumber());
            System.out.println("Class/Method: " + caller.getClassName() + "." + caller.getMethodName());
        }
        System.out.println("Provider: " + providerName);
        System.out.println("Prompt:\n" + prompt);
        System.out.println("Response:\n" + response);
        System.out.println("==================================================");
    }

    /** Alternatif isim — bazı çağıranlar geriye dönük uyumluluk için generate() bekliyor. */
    public String generate(String prompt) {
        return complete(prompt);
    }

    @Override
    public String providerName() {
        return chainLabel;
    }

    private boolean isValid(String response) {
        return response != null
                && !response.trim().isEmpty()
                && !response.trim().startsWith("[MOCK]")
                && !response.contains("Gemini service could not be reached");
    }
}
