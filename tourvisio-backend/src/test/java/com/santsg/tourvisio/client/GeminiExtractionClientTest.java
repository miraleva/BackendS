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

class GeminiExtractionClientTest {

    @Test
    void completeReturnsTextFromGeminiResponse() {
        RestTemplate restTemplate = new RestTemplate();
        GeminiExtractionClient client = new GeminiExtractionClient(restTemplate);
        ReflectionTestUtils.setField(client, "apiKey", "test-lite-key");
        ReflectionTestUtils.setField(client, "apiUrl", "https://example.test/lite/generateContent");

        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://example.test/lite/generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-goog-api-key", "test-lite-key"))
                .andExpect(content().json("""
                        {
                          "contents": [
                            {
                              "parts": [
                                { "text": "Extract some criteria" }
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
                                  { "text": "Extraction result content." }
                                ]
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        String response = client.generate("Extract some criteria");

        assertEquals("Extraction result content.", response);
        server.verify();
    }
}
