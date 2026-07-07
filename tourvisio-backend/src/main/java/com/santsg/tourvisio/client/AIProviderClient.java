package com.santsg.tourvisio.client;

/**
 * AI sağlayıcısına erişim için genel arayüz.
 * Bu arayüzü implement eden her sınıf (OpenAI, Gemini, vb.)
 * Spring tarafından {@code AIProviderClient} tipinde inject edilebilir.
 *
 * <p>API anahtarları ASLA frontend'e gönderilmez;
 * yalnızca backend'de environment variable üzerinden okunur.</p>
 */
public interface AIProviderClient {

    /**
     * Verilen prompt'u AI modeline gönderir ve metin cevabı döner.
     *
     * @param prompt Kullanıcıya veya sisteme gönderilecek tam metin
     * @return AI modelinin ürettiği cevap metni
     */
    String complete(String prompt);

    /**
     * Bu client'in bağlı olduğu sağlayıcı adını döner (loglama/monitoring için).
     *
     * @return Örn: "openai", "gemini", "ollama"
     */
    String providerName();
}
