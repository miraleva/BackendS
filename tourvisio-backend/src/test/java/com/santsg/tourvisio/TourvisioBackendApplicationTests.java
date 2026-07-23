package com.santsg.tourvisio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.santsg.tourvisio.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties = {
		"tourvisio.api.mock-mode=true",
		"tourvisio.api.test-mode=true",
		"ai.openai.api-key="
})
class TourvisioBackendApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void contextLoads() {
	}

	@Test
	void testHotelSearchSuccess() throws Exception {
		HotelSearchRequest request = new HotelSearchRequest();
		request.setLocationOrHotelName("Antalya");
		request.setCheckInDate(LocalDate.now().plusDays(5));
		request.setCheckOutDate(LocalDate.now().plusDays(12));
		request.setAdultCount(2);
		request.setCurrency("TRY");
		mockMvc.perform(post("/api/hotels/search")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(greaterThan(0))))
				.andExpect(jsonPath("$[0].name", notNullValue()))
				.andExpect(jsonPath("$[0].price", notNullValue()))
				.andExpect(jsonPath("$[0].currency", equalTo("TRY")));
	}

	@Test
	void testHotelSearchValidationError() throws Exception {
		HotelSearchRequest request = new HotelSearchRequest();
		request.setLocationOrHotelName("");
		request.setCheckInDate(null);
		request.setCheckOutDate(LocalDate.now().plusDays(12));
		request.setAdultCount(0);
		request.setCurrency("TRY");

		mockMvc.perform(post("/api/hotels/search")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status", equalTo(400)))
				.andExpect(jsonPath("$.error", equalTo("Bad Request")))
				.andExpect(jsonPath("$.details", hasSize(greaterThan(0))));
	}

	@Test
	void testFlightSearchSuccess() throws Exception {
		FlightSearchRequest request = new FlightSearchRequest(
				"Istanbul",
				"Antalya",
				LocalDate.now().plusDays(5),
				2,
				"ONE_WAY",
				"TRY");

		mockMvc.perform(post("/api/flights/search")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(greaterThan(0))))
				.andExpect(jsonPath("$[0].airline", notNullValue()))
				.andExpect(jsonPath("$[0].price", notNullValue()));
	}

	@Test
	void testReservationWorkflow() throws Exception {
		PassengerRequest passenger = new PassengerRequest(
				"Ahmet",
				"Yilmaz",
				"ahmet@gmail.com",
				"+905555555555",
				"12345678901");

		ReservationRequest request = new ReservationRequest(
				"HOTEL",
				"Rixos Premium Belek",
				"Antalya",
				LocalDate.now().plusDays(5),
				LocalDate.now().plusDays(12),
				9000.0,
				"TRY",
				null,
				List.of(passenger));

		// 1. Create Reservation
		String responseJson = mockMvc.perform(post("/api/reservations")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request))
				.requestAttr("userId", 1L))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id", notNullValue()))
				.andExpect(jsonPath("$.reservationNumber", startsWith("RES-")))
				.andExpect(jsonPath("$.passengers", hasSize(1)))
				.andExpect(jsonPath("$.passengers[0].firstName", equalTo("Ahmet")))
				.andReturn().getResponse().getContentAsString();

		// Get ID of the created reservation
		Long createdId = objectMapper.readTree(responseJson).get("id").asLong();

		// 2. Get All Reservations
		mockMvc.perform(get("/api/reservations")
				.requestAttr("userId", 1L))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(greaterThan(0))));

		// 3. Get Reservation by ID
		mockMvc.perform(get("/api/reservations/" + createdId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id", equalTo(createdId.intValue())))
				.andExpect(jsonPath("$.itemName", equalTo("Rixos Premium Belek")));

		// 4. Get Reservation by non-existent ID
		mockMvc.perform(get("/api/reservations/9999"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status", equalTo(404)))
				.andExpect(jsonPath("$.message", containsString("not found")));
	}

	@Test
	void testReservationMultiPassengerAndEditWorkflow() throws Exception {
		PassengerRequest primary = new PassengerRequest(
				"Ahmet",
				"Yilmaz",
				"ahmet@gmail.com",
				"+905555555555",
				"12345678901",
				LocalDate.of(1990, 1, 1),
				"MR",
				"TR"
		);

		PassengerRequest secondary = new PassengerRequest(
				"John",
				"Doe",
				"", // Optional email
				"", // Optional phone
				"PASSPORT123",
				LocalDate.of(2010, 5, 15),
				"CHD",
				"DE"
		);

		ReservationRequest request = new ReservationRequest(
				"HOTEL",
				"Rixos Premium Belek",
				"Antalya",
				LocalDate.now().plusDays(5),
				LocalDate.now().plusDays(12),
				9000.0,
				"TRY",
				null,
				List.of(primary, secondary));

		// 1. Create reservation
		String responseJson = mockMvc.perform(post("/api/reservations")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id", notNullValue()))
				.andExpect(jsonPath("$.passengers", hasSize(2)))
				.andExpect(jsonPath("$.passengers[0].firstName", equalTo("Ahmet")))
				.andExpect(jsonPath("$.passengers[1].firstName", equalTo("John")))
				.andExpect(jsonPath("$.passengers[1].email", anyOf(nullValue(), emptyString())))
				.andReturn().getResponse().getContentAsString();

		Long createdId = objectMapper.readTree(responseJson).get("id").asLong();

		// 2. Edit reservation (change secondary passenger name)
		secondary.setLastName("Smith");
		ReservationRequest updateRequest = new ReservationRequest(
				"HOTEL",
				"Rixos Premium Belek",
				"Antalya",
				LocalDate.now().plusDays(5),
				LocalDate.now().plusDays(12),
				9000.0,
				"TRY",
				null,
				List.of(primary, secondary));

		mockMvc.perform(put("/api/reservations/" + createdId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(updateRequest)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id", equalTo(createdId.intValue())))
				.andExpect(jsonPath("$.passengers", hasSize(2)))
				.andExpect(jsonPath("$.passengers[1].lastName", equalTo("Smith")));
	}

	@Test
	void testHotelSearchMultiTurnChatWorkflow() throws Exception {
		// Turn 1: Initial query
		ChatRequest request1 = new ChatRequest("Antalya'da 2 yetişkin için 25 Temmuz girişli 5 gece otel bakıyorum", "hotel-session-123");
		mockMvc.perform(post("/api/chat/message")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request1)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.searchType", equalTo("HOTEL_SEARCH")))
				.andExpect(jsonPath("$.missingFields", hasItem("para birimi")))
				.andExpect(jsonPath("$.chatStatus", equalTo("ACTIVE")));

		// Turn 2: Follow up with missing fields
		ChatRequest request2 = new ChatRequest("30 Temmuz çıkış olsun, para birimi TL", "hotel-session-123");
		mockMvc.perform(post("/api/chat/message")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request2)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.searchType", equalTo("HOTEL_SEARCH")))
				.andExpect(jsonPath("$.missingFields", hasSize(0)))
				.andExpect(jsonPath("$.reply", containsString("otel bulundu")))
				.andExpect(jsonPath("$.chatStatus", equalTo("ACTIVE")));
	}

	@Test
	void testFlightSearchMultiTurnChatWorkflow() throws Exception {
		// Turn 1: Initial query
		ChatRequest request1 = new ChatRequest("İstanbul'dan Ankara'ya 25 Temmuz'da uçak bakıyorum", "flight-session-123");
		mockMvc.perform(post("/api/chat/message")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request1)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.searchType", equalTo("FLIGHT_SEARCH")))
				.andExpect(jsonPath("$.missingFields", hasItem("para birimi")))
				.andExpect(jsonPath("$.missingFields", hasItem("yolcu sayısı")))
				.andExpect(jsonPath("$.chatStatus", equalTo("ACTIVE")));

		// Turn 2: Follow up
		ChatRequest request2 = new ChatRequest("Tek yön olsun, 2 yolcu, para birimi TL", "flight-session-123");
		mockMvc.perform(post("/api/chat/message")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request2)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.searchType", equalTo("FLIGHT_SEARCH")))
				.andExpect(jsonPath("$.missingFields", hasSize(0)))
				.andExpect(jsonPath("$.reply", containsString("uçuş bulundu")))
				.andExpect(jsonPath("$.chatStatus", equalTo("ACTIVE")));
	}


	@Test
	void testUnknownIntentWelcome() throws Exception {
		ChatRequest request1 = new ChatRequest("merhaba", "unknown-session-123");
		mockMvc.perform(post("/api/chat/message")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request1)))
				.andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
				.andExpect(status().isOk());
	}




	@Test
	void testWelcomeTriggerConsistency() throws Exception {
		// Session A - First message
		ChatRequest req1 = new ChatRequest("merhaba", "session-A");
		mockMvc.perform(post("/api/chat/message")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req1)))
				.andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print());

		// Session A - Second message
		ChatRequest req2 = new ChatRequest("hello", "session-A");
		mockMvc.perform(post("/api/chat/message")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req2)))
				.andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print());

		// Session B - First message
		ChatRequest req3 = new ChatRequest("hello", "session-B");
		mockMvc.perform(post("/api/chat/message")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req3)))
				.andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print());
	}


}
