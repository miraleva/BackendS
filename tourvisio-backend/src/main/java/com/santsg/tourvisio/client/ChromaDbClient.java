package com.santsg.tourvisio.client;

import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class ChromaDbClient {

    public List<String> query(String text, int nResults) {
        // Return mock matching documents retrieved from vector database
        return List.of(
            "TourVisio API üzerinden otel aramak için destinasyon (lokasyon), giriş ve çıkış tarihleri, kişi bilgileri girilmelidir.",
            "TourVisio API üzerinden uçuş aramak için kalkış ve varış yerleri, gidiş/dönüş tarihleri ve yolcu sayısı girilmelidir."
        );
    }
}
