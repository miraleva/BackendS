
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ScratchTest {
    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("GEMINI_API_KEY");
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + apiKey;
        
        String prompt = "The user's travel search has been completed successfully. Here are the search results in JSON format:\\n" +
            "Search Type: HOTEL_SEARCH\\n" +
            "Results:\\n[{}]\\n\\n" +
            "Write a warm, polite, and engaging assistant response summarizing these results. Do NOT use terse lists like 'En iyi teklif: X'. Instead, write 1-2 natural sentences per top recommendation. Include the following context naturally in your response:\\n\\nFound 5 matches for your criteria. Here they are:\\n\\n" +
            "IMPORTANT RULES:\\n" +
            "1. Write the response in the official language of Turkey (Turkish).\\n" +
            "2. Only mention facts from the provided JSON results.\\n" +
            "3. Never invent nicer names for raw system/sandbox data (e.g. if the room name is 'low level yerel dil' or 'BUILD131', present it exactly as is without fabricating a nicer name).\\n" +
            "4. Return ONLY the assistant's summary response, with no notes or extra text.";

        String body = "{\"contents\":[{\"parts\":[{\"text\":\"" + prompt.replace("\n", "\\n").replace("\"", "\\\"") + "\"}]}]}";
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
            
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("Status: " + response.statusCode());
        System.out.println("Body: " + response.body());
    }
}

