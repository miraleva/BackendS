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
		HotelSearchRequest request = new HotelSearchRequest(
				"Antalya",
				LocalDate.now().plusDays(5),
				LocalDate.now().plusDays(12),
				2,
				"TRY"
		);

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
		HotelSearchRequest request = new HotelSearchRequest(
				"", // empty location
				null, // check-in date null
				LocalDate.now().plusDays(12),
				0, // invalid adult count
				"TRY"
		);

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
				"TRY"
		);

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
				"12345678901"
		);

		ReservationRequest request = new ReservationRequest(
				"HOTEL",
				"Rixos Premium Belek",
				"Antalya",
				LocalDate.now().plusDays(5),
				LocalDate.now().plusDays(12),
				9000.0,
				"TRY",
				List.of(passenger)
		);

		// 1. Create Reservation
		String responseJson = mockMvc.perform(post("/api/reservations")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id", notNullValue()))
				.andExpect(jsonPath("$.reservationNumber", startsWith("RES-")))
				.andExpect(jsonPath("$.passengers", hasSize(1)))
				.andExpect(jsonPath("$.passengers[0].firstName", equalTo("Ahmet")))
				.andReturn().getResponse().getContentAsString();

		// Get ID of the created reservation
		Long createdId = objectMapper.readTree(responseJson).get("id").asLong();

		// 2. Get All Reservations
		mockMvc.perform(get("/api/reservations"))
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
}
