package com.santsg.tourvisio.service;

import com.santsg.tourvisio.dto.PassengerRequest;
import com.santsg.tourvisio.dto.ReservationRequest;
import com.santsg.tourvisio.entity.Reservation;
import com.santsg.tourvisio.repository.ReservationRepository;
import com.santsg.tourvisio.chat.SearchCriteria;
import com.santsg.tourvisio.chat.ChatSessionStore;
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

    @Mock
    private ChatSessionStore chatSessionStore;

    @InjectMocks
    private ReservationService reservationService;

    private ReservationRequest request;

    @BeforeEach
    void setUp() {
        PassengerRequest passenger = new PassengerRequest("Ahmet", "Yılmaz", "ahmet@example.com", "+905551112233", "10000000146");
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
        verify(emailService, times(1)).sendReservationConfirmationEmail(any(), eq("ahmet@example.com"), eq("Ahmet Yılmaz"), any());
    }

    @Test
    void testCreateAuthenticatedUserReservation() {
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Reservation result = reservationService.createReservation(request, 42L);

        assertNotNull(result);
        assertFalse(result.getIsGuest());
        assertEquals(42L, result.getUserId());
        verify(emailService, times(1)).sendReservationConfirmationEmail(any(), eq("ahmet@example.com"), eq("Ahmet Yılmaz"), any());
    }

    @Test
    void testAutoFillPassengerBirthDates() {
        // Arrange
        String sessionId = "test-session-123";
        request.setChatSessionId(sessionId);
        
        PassengerRequest adult = request.getPassengers().get(0);
        PassengerRequest child = new PassengerRequest("Mert", "Yılmaz", "mert@example.com", "", "10000000150");
        PassengerRequest infant = new PassengerRequest("Bebek", "Yılmaz", "bebek@example.com", "", "10000000160");
        
        request.setPassengers(List.of(adult, child, infant));
        
        SearchCriteria criteria = new SearchCriteria();
        criteria.setAdultCount(1);
        criteria.setChildCount(1);
        criteria.setChildAges(List.of(5));
        criteria.setInfantCount(1);
        criteria.setInfantAges(List.of(0));
        criteria.getInfantAgesInMonths().add(10); // 10 months old
        
        when(chatSessionStore.getOrCreate(sessionId)).thenReturn(criteria);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Reservation result = reservationService.createReservation(request, null);

        // Assert
        assertNotNull(result);
        List<PassengerRequest> updatedPassengers = request.getPassengers();
        
        // Adult
        assertEquals(LocalDate.of(1990, 1, 1), updatedPassengers.get(0).getBirthDate());
        
        // Child
        LocalDate expectedChildBirthdate = request.getStartDate().minusYears(5);
        assertEquals(expectedChildBirthdate, updatedPassengers.get(1).getBirthDate());
        assertEquals("CHD", updatedPassengers.get(1).getGender());
        
        // Infant
        LocalDate expectedInfantBirthdate = request.getStartDate().minusMonths(10);
        assertEquals(expectedInfantBirthdate, updatedPassengers.get(2).getBirthDate());
        assertEquals("INF", updatedPassengers.get(2).getGender());
    }
}
