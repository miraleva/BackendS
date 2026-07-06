package com.santsg.tourvisio.service;

import com.santsg.tourvisio.client.ChromaDbClient;
import com.santsg.tourvisio.client.OllamaClient;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class RagService {

    private final ChromaDbClient chromaDbClient;
    private final OllamaClient ollamaClient;

    public RagService(ChromaDbClient chromaDbClient, OllamaClient ollamaClient) {
        this.chromaDbClient = chromaDbClient;
        this.ollamaClient = ollamaClient;
    }

    public String retrieveAndGenerate(String query) {
        // Retrieve relevant mock documents from the vector database
        List<String> docs = chromaDbClient.query(query, 2);
        
        // Construct the prompt with retrieved context
        String prompt = "Soru: " + query + "\nBağlam:\n" + String.join("\n", docs);
        
        // Generate mock response via Ollama
        return ollamaClient.generate(prompt);
    }
}
