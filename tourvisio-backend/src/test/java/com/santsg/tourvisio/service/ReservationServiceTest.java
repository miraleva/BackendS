package com.santsg.tourvisio.service;

import com.santsg.tourvisio.dto.PassengerRequest;
import com.santsg.tourvisio.dto.ReservationRequest;
import com.santsg.tourvisio.entity.Reservation;
import com.santsg.tourvisio.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private ReservationService reservationService;

    private ReservationRequest request;

    @BeforeEach
    void setUp() {
        PassengerRequest passenger = new PassengerRequest("Ahmet", "Yılmaz", "ahmet@example.com", "+905551112233", "12345678901");
        request = new ReservationRequest();
        request.setType("HOTEL");
        request.setItemName("Grand Hotel");
        request.setDestination("Antalya");
        request.setStartDate(LocalDate.now().plusDays(1));
        request.setEndDate(LocalDate.now().plusDays(5));
        request.setTotalPrice(1500.0);
        request.setCurrency("TRY");
        request.setPassengers(List.of(passenger));
    }

    @Test
    void testCreateGuestReservation() {
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Reservation result = reservationService.createReservation(request, null);

        assertNotNull(result);
        assertTrue(result.getIsGuest());
        assertNull(result.getUserId());
        assertNotNull(result.getReservationNumber());
        verify(emailService, times(1)).sendReservationConfirmationEmail(any(), eq("ahmet@example.com"), eq("Ahmet Yılmaz"));
    }

    @Test
    void testCreateAuthenticatedUserReservation() {
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Reservation result = reservationService.createReservation(request, 42L);

        assertNotNull(result);
        assertFalse(result.getIsGuest());
        assertEquals(42L, result.getUserId());
        verify(emailService, times(1)).sendReservationConfirmationEmail(any(), eq("ahmet@example.com"), eq("Ahmet Yılmaz"));
    }
}
