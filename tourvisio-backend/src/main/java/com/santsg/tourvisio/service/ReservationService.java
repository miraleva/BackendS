package com.santsg.tourvisio.service;

import com.santsg.tourvisio.dto.PassengerRequest;
import com.santsg.tourvisio.dto.ReservationRequest;
import com.santsg.tourvisio.entity.Passenger;
import com.santsg.tourvisio.entity.Reservation;
import com.santsg.tourvisio.exception.ResourceNotFoundException;
import com.santsg.tourvisio.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;

    public ReservationService(ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }

    @Transactional
    public Reservation createReservation(ReservationRequest request) {
        // Generate a unique reservation number (e.g. RES-ABC12D)
        String reservationNum = "RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Reservation reservation = Reservation.builder()
                .reservationNumber(reservationNum)
                .type(request.getType().toUpperCase())
                .itemName(request.getItemName())
                .destination(request.getDestination())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .totalPrice(request.getTotalPrice())
                .currency(request.getCurrency())
                .build();

        List<Passenger> passengers = new ArrayList<>();
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

        reservation.setPassengers(passengers);
        return reservationRepository.save(reservation);
    }

    public List<Reservation> getAllReservations() {
        return reservationRepository.findAll();
    }

    public Reservation getReservationById(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation with ID " + id + " not found"));
    }
}
