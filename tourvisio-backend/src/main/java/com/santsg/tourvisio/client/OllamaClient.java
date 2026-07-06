package com.santsg.tourvisio.client;

import org.springframework.stereotype.Component;

@Component
public class OllamaClient {

    public String generate(String prompt) {
        // Return a mock response from the LLM
        return "Ollama Mock Response: [Sorgunuza yönelik RAG ile zenginleştirilmiş yanıt oluşturuldu]";
    }
}
