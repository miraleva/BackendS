package com.santsg.tourvisio.service;

import com.santsg.tourvisio.dto.PassengerPrefillResponse;
import com.santsg.tourvisio.dto.PassengerRequest;
import com.santsg.tourvisio.dto.ReservationRequest;
import com.santsg.tourvisio.entity.Passenger;
import com.santsg.tourvisio.entity.Reservation;
import com.santsg.tourvisio.entity.User;
import com.santsg.tourvisio.exception.ResourceNotFoundException;
import com.santsg.tourvisio.repository.ReservationRepository;
import com.santsg.tourvisio.repository.UserRepository;
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
    private final UserRepository userRepository;

    public ReservationService(ReservationRepository reservationRepository, EmailService emailService,
            UserRepository userRepository) {
        this.reservationRepository = reservationRepository;
        this.emailService = emailService;
        this.userRepository = userRepository;
    }

    public PassengerPrefillResponse getPrefillData(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User with ID " + userId + " not found"));
        return PassengerPrefillResponse.builder()
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phoneNumber(user.getPhone())
                .build();
    }

    private void validateReservationRequest(ReservationRequest request) {
        if (request.getPassengers() == null || request.getPassengers().isEmpty()) {
            throw new IllegalArgumentException("Reservation must have at least one passenger");
        }

        PassengerRequest primary = request.getPassengers().get(0);

        if (primary.getEmail() == null || primary.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Primary passenger email cannot be blank");
        }
        if (!primary.getEmail()
                .matches("^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$")) {
            throw new IllegalArgumentException("Invalid primary passenger email format");
        }

        if (primary.getPhoneNumber() == null || primary.getPhoneNumber().trim().isEmpty()) {
            throw new IllegalArgumentException("Primary passenger phone number cannot be blank");
        }

        for (int i = 0; i < request.getPassengers().size(); i++) {
            PassengerRequest pr = request.getPassengers().get(i);
            String nat = pr.getNationality();
            String idNum = pr.getIdentityNumber();
            String pName = (pr.getFirstName() != null ? pr.getFirstName() : "") + " "
                    + (pr.getLastName() != null ? pr.getLastName() : "");
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
                    throw new IllegalArgumentException(
                            pName + " için T.C. Kimlik numarası geçersiz (11 hane olmalı ve 0 ile başlamamalı).");
                }
            } else {
                if (idNum.trim().length() < 5) {
                    throw new IllegalArgumentException(
                            pName + " için Pasaport numarası geçersiz (en az 5 karakter olmalıdır).");
                }
            }

            if (pr.getBirthDate() == null) {
                throw new IllegalArgumentException(pName + " için doğum tarihi boş olamaz");
            }

            if (pr.getBirthDate().isAfter(LocalDate.now())) {
                throw new IllegalArgumentException(pName + " için doğum tarihi geçmişte olmalıdır.");
            }

            int ageYears = java.time.Period.between(pr.getBirthDate(), LocalDate.now()).getYears();

            if (i == 0 && ageYears < 18) {
                throw new IllegalArgumentException("Rezervasyonu yapan kişi 18 yaşından büyük (veya en az 18 yaşında) olmalıdır.");
            }

            String genderOrType = pr.getGender();
            if ("CHD".equalsIgnoreCase(genderOrType) || (genderOrType != null && genderOrType.toUpperCase().contains("CHILD"))) {
                if (ageYears >= 18) {
                    throw new IllegalArgumentException(pName + " (çocuk yolcu) için doğum tarihi 18 yaşından küçük olmalıdır.");
                }
                if (ageYears < 2) {
                    throw new IllegalArgumentException(pName + " (çocuk yolcu) en az 2 yaşında olmalıdır.");
                }
            } else if ("INF".equalsIgnoreCase(genderOrType) || (genderOrType != null && genderOrType.toUpperCase().contains("INFANT"))) {
                if (ageYears >= 2) {
                    throw new IllegalArgumentException(pName + " (bebek yolcu) için doğum tarihi 2 yaşından küçük olmalıdır.");
                }
            }
        }
    }

    @Transactional
    public Reservation createReservation(ReservationRequest request, Long userId) {
        validateReservationRequest(request);

        // Generate a unique PNR / reservation number (e.g. PNR-849201)
        String reservationNum = "PNR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();

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
                .chatSessionId(request.getChatSessionId())
                .imageUrl(request.getImageUrl())
                .flightNumber(request.getFlightNumber())
                .departureAirportCode(request.getDepartureAirportCode())
                .arrivalAirportCode(request.getArrivalAirportCode())
                .departureCity(request.getDepartureCity())
                .arrivalCity(request.getArrivalCity())
                .departureTime(request.getDepartureTime())
                .arrivalTime(request.getArrivalTime())
                .ticketClass(request.getTicketClass())
                .baggageAllowance(request.getBaggageAllowance())
                .roomType(request.getRoomType())
                .boardType(request.getBoardType())
                .checkInTime(request.getCheckInTime())
                .checkOutTime(request.getCheckOutTime())
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
        String fullName = (primary.getFirstName() != null ? primary.getFirstName() : "") + " "
                + (primary.getLastName() != null ? primary.getLastName() : "");
        emailService.sendReservationConfirmationEmail(savedReservation, primary.getEmail(), fullName.trim(),
                request.getLang());

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
        reservation.setChatSessionId(request.getChatSessionId());
        reservation.setImageUrl(request.getImageUrl());
        reservation.setFlightNumber(request.getFlightNumber());
        reservation.setDepartureAirportCode(request.getDepartureAirportCode());
        reservation.setArrivalAirportCode(request.getArrivalAirportCode());
        reservation.setDepartureCity(request.getDepartureCity());
        reservation.setArrivalCity(request.getArrivalCity());
        reservation.setDepartureTime(request.getDepartureTime());
        reservation.setArrivalTime(request.getArrivalTime());
        reservation.setTicketClass(request.getTicketClass());
        reservation.setBaggageAllowance(request.getBaggageAllowance());
        reservation.setRoomType(request.getRoomType());
        reservation.setBoardType(request.getBoardType());
        reservation.setCheckInTime(request.getCheckInTime());
        reservation.setCheckOutTime(request.getCheckOutTime());

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

    public void sendEmailForReservation(Long reservationId, String overrideEmail) {
        Reservation reservation = getReservationById(reservationId);
        String recipientEmail = overrideEmail;

        if ((recipientEmail == null || recipientEmail.isBlank()) && reservation.getPassengers() != null && !reservation.getPassengers().isEmpty()) {
            Passenger primary = reservation.getPassengers().get(0);
            recipientEmail = primary.getEmail();
        }

        if (recipientEmail == null || recipientEmail.isBlank()) {
            recipientEmail = "destek@sanny.com";
        }

        String customerName = reservation.getPrimaryGuestName();
        if (customerName == null || customerName.isBlank()) {
            customerName = "Değerli Misafirimiz";
        }

        emailService.sendReservationConfirmationEmail(reservation, recipientEmail, customerName, "tr");
    }

    public List<Reservation> getAllReservations() {
        return reservationRepository.findAll();
    }

    public List<Reservation> getReservationsByUserId(Long userId) {
        if (userId == null) {
            return new ArrayList<>();
        }
        return reservationRepository.findByUserIdOrderByIdDesc(userId);
    }

    public Reservation getReservationById(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation with ID " + id + " not found"));
    }

    @Transactional
    public void cancelReservation(Long id) {
        Reservation reservation = getReservationById(id);
        reservation.setStatus("CANCELLED");
        reservationRepository.save(reservation);
    }
}