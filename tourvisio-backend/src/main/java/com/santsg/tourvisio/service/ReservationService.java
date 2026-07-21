package com.santsg.tourvisio.service;

import com.santsg.tourvisio.dto.PassengerRequest;
import com.santsg.tourvisio.dto.ReservationRequest;
import com.santsg.tourvisio.entity.Passenger;
import com.santsg.tourvisio.entity.Reservation;
import com.santsg.tourvisio.exception.ResourceNotFoundException;
import com.santsg.tourvisio.repository.ReservationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final EmailService emailService;

    public ReservationService(ReservationRepository reservationRepository, EmailService emailService) {
        this.reservationRepository = reservationRepository;
        this.emailService = emailService;
    }

    @Transactional
    public Reservation createReservation(ReservationRequest request) {
        return createReservation(request, null);
    }

    @Transactional
    public Reservation createReservation(ReservationRequest request, Long userId) {
        // Generate a unique reservation number (e.g. RES-ABC12D)
        String reservationNum = "RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        boolean isGuest = (userId == null);

        Reservation reservation = Reservation.builder()
                .reservationNumber(reservationNum)
                .userId(userId)
                .isGuest(isGuest)
                .type(request.getType() != null ? request.getType().toUpperCase() : "HOTEL")
                .itemName(request.getItemName())
                .destination(request.getDestination())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .totalPrice(request.getTotalPrice())
                .currency(request.getCurrency())
                .build();

        List<Passenger> passengers = new ArrayList<>();
        if (request.getPassengers() != null) {
            for (PassengerRequest pr : request.getPassengers()) {
                Passenger passenger = Passenger.builder()
                        .firstName(pr.getFirstName())
                        .lastName(pr.getLastName())
                        .email(pr.getEmail())
                        .phoneNumber(pr.getPhoneNumber())
                        .identityNumber(pr.getIdentityNumber())
                        .birthDate(pr.getBirthDate())
                        .gender(pr.getGender())
                        .nationality(pr.getNationality())
                        .reservation(reservation)
                        .build();
                passengers.add(passenger);
            }
        }

        reservation.setPassengers(passengers);
        Reservation saved = reservationRepository.save(reservation);

        // Extract primary passenger email & name for confirmation email dispatch
        String recipientEmail = null;
        String customerName = null;

        if (!passengers.isEmpty()) {
            Passenger primary = passengers.get(0);
            recipientEmail = primary.getEmail();
            customerName = (primary.getFirstName() != null ? primary.getFirstName() : "")
                    + " " + (primary.getLastName() != null ? primary.getLastName() : "");
            customerName = customerName.trim();
        }

        log.info("[ReservationService] Created reservation {} (isGuest={}, userId={}). Dispatching async confirmation email to {}",
                saved.getReservationNumber(), isGuest, userId, recipientEmail);

        if (recipientEmail != null && !recipientEmail.isBlank()) {
            try {
                emailService.sendReservationConfirmationEmail(saved, recipientEmail, customerName);
            } catch (Exception e) {
                log.error("[ReservationService] Async email dispatch error: {}", e.getMessage());
            }
        }

        return saved;
    }

    public List<Reservation> getAllReservations() {
        return reservationRepository.findAll();
    }

    public Reservation getReservationById(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation with ID " + id + " not found"));
    }
}
