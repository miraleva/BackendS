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
                return stripStrayArabicScript(response);
            }

            log.warn("[AIFallbackChain:{}] '{}' sağlayıcısı geçersiz yanıt döndürdü, bir sonrakine geçiliyor.",
                    chainLabel, provider.providerName());
        }

        log.error("[AIFallbackChain:{}] Tüm sağlayıcılar ({} adet) başarısız oldu.", chainLabel, providers.size());
        return lastResponse;
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

    /**
     * Ücretsiz yedek modeller (ör. OpenRouter free tier) ara sıra üretilen metnin
     * içine, hiçbir bağlamla ilgisi olmayan Arapça alfabe karakterleri karıştırıyor
     * (ör. "tarihleriniz" → "tarihوهleriniz", "maalesef" → "maalesف"). Uygulama
     * sadece Latin/Türkçe ve Kiril (Rusça desteği) alfabelerini kullanıyor, bu
     * yüzden Arapça blok karakterlerini güvenle temizleyip kelimeyi olduğu gibi
     * bırakabiliyoruz.
     */
    private static String stripStrayArabicScript(String response) {
        if (response == null || response.isEmpty()) {
            return response;
        }
        String cleaned = response.replaceAll("[\\u0600-\\u06FF\\u0750-\\u077F\\u08A0-\\u08FF\\uFB50-\\uFDFF\\uFE70-\\uFEFF]", "");
        return cleaned.equals(response) ? response : cleaned;
    }
}
