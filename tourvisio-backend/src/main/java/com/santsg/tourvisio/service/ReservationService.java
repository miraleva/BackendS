package com.santsg.tourvisio.service;

import com.santsg.tourvisio.dto.PassengerRequest;
import com.santsg.tourvisio.dto.ReservationRequest;
import com.santsg.tourvisio.entity.Passenger;
import com.santsg.tourvisio.entity.Reservation;
import com.santsg.tourvisio.exception.ResourceNotFoundException;
import com.santsg.tourvisio.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final EmailService emailService;

    public ReservationService(ReservationRepository reservationRepository, EmailService emailService) {
        this.reservationRepository = reservationRepository;
        this.emailService = emailService;
    }

    private void validateReservationRequest(ReservationRequest request) {
        if (request.getPassengers() == null || request.getPassengers().isEmpty()) {
            throw new IllegalArgumentException("Reservation must have at least one passenger");
        }

        PassengerRequest primary = request.getPassengers().get(0);

        if (primary.getEmail() == null || primary.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Primary passenger email cannot be blank");
        }
        if (!primary.getEmail().matches("^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$")) {
            throw new IllegalArgumentException("Invalid primary passenger email format");
        }

        if (primary.getPhoneNumber() == null || primary.getPhoneNumber().trim().isEmpty()) {
            throw new IllegalArgumentException("Primary passenger phone number cannot be blank");
        }

        for (int i = 0; i < request.getPassengers().size(); i++) {
            PassengerRequest pr = request.getPassengers().get(i);
            String nat = pr.getNationality();
            String idNum = pr.getIdentityNumber();
            String pName = (pr.getFirstName() != null ? pr.getFirstName() : "") + " " + (pr.getLastName() != null ? pr.getLastName() : "");
            if (pName.trim().isEmpty()) {
                pName = (i + 1) + ". Yolcu";
            }

            if (nat == null || nat.trim().isEmpty()) {
                throw new IllegalArgumentException(pName + " için uyruk boş olamaz");
            }

            if (idNum == null || idNum.trim().isEmpty()) {
                throw new IllegalArgumentException(pName + " için T.C. Kimlik / Pasaport numarası boş olamaz");
            }

            if ("TR".equalsIgnoreCase(nat.trim())) {
                if (!idNum.matches("^[1-9]\\d{10}$")) {
                    throw new IllegalArgumentException(pName + " için T.C. Kimlik numarası geçersiz (11 hane olmalı ve 0 ile başlamamalı).");
                }
            } else {
                if (idNum.trim().length() < 5) {
                    throw new IllegalArgumentException(pName + " için Pasaport numarası geçersiz (en az 5 karakter olmalıdır).");
                }
            }

            if (pr.getBirthDate() == null) {
                throw new IllegalArgumentException(pName + " için doğum tarihi boş olamaz");
            }

            if (pr.getBirthDate().isAfter(LocalDate.now())) {
                throw new IllegalArgumentException(pName + " için doğum tarihi geçmişte olmalıdır.");
            }

            if (i == 0) {
                LocalDate eighteenYearsAgo = LocalDate.now().minusYears(18);
                if (pr.getBirthDate().isAfter(eighteenYearsAgo)) {
                    throw new IllegalArgumentException("Rezervasyonu yapan kişi 18 yaşından büyük olmalıdır.");
                }
            }
        }
    }

    @Transactional
    public Reservation createReservation(ReservationRequest request, Long userId) {
        validateReservationRequest(request);

        // Generate a unique reservation number (e.g. RES-ABC12D)
        String reservationNum = "RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Reservation reservation = Reservation.builder()
                .reservationNumber(reservationNum)
                .userId(userId)
                .isGuest(userId == null)
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
        Reservation savedReservation = reservationRepository.save(reservation);

        // Send confirmation email
        PassengerRequest primary = request.getPassengers().get(0);
        String fullName = (primary.getFirstName() != null ? primary.getFirstName() : "") + " " + (primary.getLastName() != null ? primary.getLastName() : "");
        emailService.sendReservationConfirmationEmail(savedReservation, primary.getEmail(), fullName.trim());

        return savedReservation;
    }

    @Transactional
    public Reservation updateReservation(Long id, ReservationRequest request) {
        validateReservationRequest(request);

        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation with ID " + id + " not found"));

        reservation.setType(request.getType().toUpperCase());
        reservation.setItemName(request.getItemName());
        reservation.setDestination(request.getDestination());
        reservation.setStartDate(request.getStartDate());
        reservation.setEndDate(request.getEndDate());
        reservation.setTotalPrice(request.getTotalPrice());
        reservation.setCurrency(request.getCurrency());

        // Cascade ALL + orphanRemoval: clear and re-add
        reservation.getPassengers().clear();
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
            reservation.getPassengers().add(passenger);
        }

        return reservationRepository.save(reservation);
    }

    public List<Reservation> getAllReservations() {
        return reservationRepository.findAll();
    }

    public List<Reservation> getReservationsByUserId(Long userId) {
        if (userId == null) {
            return new ArrayList<>();
        }
        return reservationRepository.findByUserId(userId);
    }

    public Reservation getReservationById(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation with ID " + id + " not found"));
    }
}