package com.santsg.tourvisio.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiClientTest {

    @Test
    void completeReturnsTextFromGeminiResponse() {
        RestTemplate restTemplate = new RestTemplate();
        GeminiClient client = new GeminiClient(restTemplate);
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        ReflectionTestUtils.setField(client, "apiUrl", "https://example.test/generateContent");

        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://example.test/generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-goog-api-key", "test-key"))
                .andExpect(content().json("""
                        {
                          "contents": [
                            {
                              "parts": [
                                { "text": "Explain how AI works in a few words" }
                              ]
                            }
                          ]
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "candidates": [
                            {
                              "content": {
                                "parts": [
                                  { "text": "AI uses patterns to learn from data." }
                                ]
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        String response = client.generate("Explain how AI works in a few words");

        assertEquals("AI uses patterns to learn from data.", response);
        server.verify();
    }
}
