package com.santsg.tourvisio.service;

import com.santsg.tourvisio.dto.PassengerRequest;
import com.santsg.tourvisio.dto.ReservationRequest;
import com.santsg.tourvisio.entity.Notification;
import com.santsg.tourvisio.entity.Passenger;
import com.santsg.tourvisio.entity.Reservation;
import com.santsg.tourvisio.entity.User;
import com.santsg.tourvisio.exception.ResourceNotFoundException;
import com.santsg.tourvisio.repository.NotificationRepository;
import com.santsg.tourvisio.repository.ReservationRepository;
import com.santsg.tourvisio.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
@Slf4j
public class ReservationService {

        private final ReservationRepository reservationRepository;
        private final EmailService emailService;
        private final UserRepository userRepository;
        private final NotificationRepository notificationRepository;

        public ReservationService(
                        ReservationRepository reservationRepository,
                        EmailService emailService,
                        UserRepository userRepository,
                        NotificationRepository notificationRepository) {
                this.reservationRepository = reservationRepository;
                this.emailService = emailService;
                this.userRepository = userRepository;
                this.notificationRepository = notificationRepository;
        }

        // =========================================================
        // CREATE RESERVATION
        // =========================================================

        @Transactional
        public Reservation createReservation(
                        ReservationRequest request,
                        Long userId) {

                validateReservationRequest(request);

                log.info(
                                "[ReservationService] createReservation başladı. userId={}",
                                userId);

                User user = null;

                if (userId != null) {
                        user = userRepository
                                        .findById(userId)
                                        .orElse(null);
                }

                log.info(
                                "[ReservationService] Kullanıcı bulundu mu? {}",
                                user != null);

                if (user != null) {
                        log.info(
                                        "[ReservationService] userId={}, email={}, notifyInApp={}, notifyBookingConfirmations={}",
                                        user.getId(),
                                        user.getEmail(),
                                        user.getNotifyInApp(),
                                        user.getNotifyBookingConfirmations());
                }

                // Remote main'deki rezervasyon numarası formatını koruyoruz.
                String reservationNum = "TV-" + (100000 + new Random().nextInt(900000));

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

                log.info(
                                "[ReservationService] Yeni rezervasyon oluşturuldu: PNR={}, User={}",
                                reservationNum,
                                userId);

                log.info(
                                "[ReservationService] Rezervasyon kaydedildi. id={}, reservationNumber={}, userId={}",
                                savedReservation.getId(),
                                savedReservation.getReservationNumber(),
                                savedReservation.getUserId());

                // =====================================================
                // TOURVISIO API LOG
                // =====================================================

                try {
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

                        mapper.registerModule(
                                        new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

                        String reqJson = mapper.writeValueAsString(request);

                        Map<String, Object> respMap = new HashMap<>();

                        respMap.put("id", savedReservation.getId());
                        respMap.put(
                                        "reservationNumber",
                                        savedReservation.getReservationNumber());
                        respMap.put(
                                        "itemName",
                                        savedReservation.getItemName());
                        respMap.put(
                                        "type",
                                        savedReservation.getType());
                        respMap.put(
                                        "destination",
                                        savedReservation.getDestination());
                        respMap.put(
                                        "startDate",
                                        savedReservation.getStartDate() != null
                                                        ? savedReservation.getStartDate().toString()
                                                        : null);
                        respMap.put(
                                        "endDate",
                                        savedReservation.getEndDate() != null
                                                        ? savedReservation.getEndDate().toString()
                                                        : null);
                        respMap.put(
                                        "totalPrice",
                                        savedReservation.getTotalPrice());
                        respMap.put(
                                        "currency",
                                        savedReservation.getCurrency());
                        respMap.put(
                                        "status",
                                        "SUCCESS");

                        String respJson = mapper.writeValueAsString(respMap);

                        com.santsg.tourvisio.config.TourVisioApiMonitor.logCall(
                                        "POST",
                                        "/api/tourvisio/booking",
                                        2000L,
                                        200,
                                        "OK",
                                        null,
                                        true,
                                        reqJson,
                                        respJson);

                } catch (Exception e) {
                        log.error(
                                        "Failed to write TourVisio GDS API log for reservation booking",
                                        e);
                }

                // =====================================================
                // CONFIRMATION EMAIL
                // =====================================================

                PassengerRequest primary = request.getPassengers().get(0);

                String fullName = (primary.getFirstName() != null
                                ? primary.getFirstName()
                                : "")
                                + " "
                                + (primary.getLastName() != null
                                                ? primary.getLastName()
                                                : "");

                emailService.sendReservationConfirmationEmail(
                                savedReservation,
                                primary.getEmail(),
                                fullName.trim(),
                                request.getLang());

                // =====================================================
                // IN-APP NOTIFICATION
                // =====================================================

                createBookingNotification(
                                savedReservation,
                                user);

                return savedReservation;
        }

        // =========================================================
        // CREATE BOOKING NOTIFICATION
        // =========================================================

        private void createBookingNotification(
                        Reservation reservation,
                        User user) {

                log.info(
                                "[ReservationService] createBookingNotification çağrıldı. reservationId={}, userId={}",
                                reservation != null
                                                ? reservation.getId()
                                                : null,
                                user != null
                                                ? user.getId()
                                                : null);

                if (user == null) {
                        log.warn(
                                        "[ReservationService] Bildirim oluşturulmadı: user null");
                        return;
                }

                if (!Boolean.TRUE.equals(user.getNotifyInApp())) {
                        log.warn(
                                        "[ReservationService] Bildirim oluşturulmadı: notifyInApp=false. userId={}",
                                        user.getId());
                        return;
                }

                if (!Boolean.TRUE.equals(
                                user.getNotifyBookingConfirmations())) {
                        log.warn(
                                        "[ReservationService] Bildirim oluşturulmadı: notifyBookingConfirmations=false. userId={}",
                                        user.getId());
                        return;
                }

                String type = reservation.getType() != null
                                ? reservation.getType().toUpperCase()
                                : "";

                String title;
                String message;

                if ("HOTEL".equals(type)) {

                        title = "Otel rezervasyonunuz onaylandı";

                        message = reservation.getItemName()
                                        + " rezervasyonunuz başarıyla oluşturuldu."
                                        + " Rezervasyon No: "
                                        + reservation.getReservationNumber();

                } else if ("FLIGHT".equals(type)) {

                        title = "Uçuş rezervasyonunuz onaylandı";

                        message = reservation.getDestination()
                                        + " uçuş rezervasyonunuz başarıyla oluşturuldu."
                                        + " Rezervasyon No: "
                                        + reservation.getReservationNumber();

                } else {

                        title = "Rezervasyonunuz onaylandı";

                        message = reservation.getItemName()
                                        + " rezervasyonunuz başarıyla oluşturuldu."
                                        + " Rezervasyon No: "
                                        + reservation.getReservationNumber();
                }

                Notification notification = Notification.builder()
                                .user(user)
                                .title(title)
                                .message(message)
                                .type("BOOKING_CONFIRMATION")
                                .isRead(false)
                                .build();

                try {

                        Notification savedNotification = notificationRepository.save(notification);

                        log.info(
                                        "[ReservationService] BİLDİRİM OLUŞTURULDU. notificationId={}, userId={}, type={}, title={}",
                                        savedNotification.getId(),
                                        user.getId(),
                                        savedNotification.getType(),
                                        savedNotification.getTitle());

                } catch (Exception e) {

                        log.error(
                                        "[ReservationService] BİLDİRİM KAYDEDİLEMEDİ. userId={}, reservationId={}, hata={}",
                                        user.getId(),
                                        reservation.getId(),
                                        e.getMessage(),
                                        e);

                        throw e;
                }
        }

        // =========================================================
        // UPDATE RESERVATION
        // =========================================================

        @Transactional
        public Reservation updateReservation(
                        Long id,
                        ReservationRequest request) {

                validateReservationRequest(request);

                Reservation reservation = reservationRepository
                                .findById(id)
                                .orElseThrow(
                                                () -> new ResourceNotFoundException(
                                                                "Reservation with ID "
                                                                                + id
                                                                                + " not found"));

                reservation.setType(
                                request.getType().toUpperCase());
                reservation.setItemName(
                                request.getItemName());
                reservation.setDestination(
                                request.getDestination());
                reservation.setStartDate(
                                request.getStartDate());
                reservation.setEndDate(
                                request.getEndDate());
                reservation.setTotalPrice(
                                request.getTotalPrice());
                reservation.setCurrency(
                                request.getCurrency());
                reservation.setChatSessionId(
                                request.getChatSessionId());
                reservation.setImageUrl(
                                request.getImageUrl());
                reservation.setFlightNumber(
                                request.getFlightNumber());
                reservation.setDepartureAirportCode(
                                request.getDepartureAirportCode());
                reservation.setArrivalAirportCode(
                                request.getArrivalAirportCode());
                reservation.setDepartureCity(
                                request.getDepartureCity());
                reservation.setArrivalCity(
                                request.getArrivalCity());
                reservation.setDepartureTime(
                                request.getDepartureTime());
                reservation.setArrivalTime(
                                request.getArrivalTime());
                reservation.setTicketClass(
                                request.getTicketClass());
                reservation.setBaggageAllowance(
                                request.getBaggageAllowance());
                reservation.setRoomType(
                                request.getRoomType());
                reservation.setBoardType(
                                request.getBoardType());
                reservation.setCheckInTime(
                                request.getCheckInTime());
                reservation.setCheckOutTime(
                                request.getCheckOutTime());

                // Cascade ALL + orphanRemoval:
                // mevcut yolcuları temizleyip yeniden ekliyoruz.
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

                        reservation
                                        .getPassengers()
                                        .add(passenger);
                }

                Reservation updatedReservation = reservationRepository.save(reservation);

                log.info(
                                "[ReservationService] Rezervasyon güncellendi. id={}, reservationNumber={}",
                                updatedReservation.getId(),
                                updatedReservation.getReservationNumber());

                return updatedReservation;
        }

        // =========================================================
        // GET ALL
        // =========================================================

        public List<Reservation> getAllReservations() {
                return reservationRepository.findAll();
        }

        // =========================================================
        // GET BY ID
        // =========================================================

        public Reservation getReservationById(Long id) {

                return reservationRepository
                                .findById(id)
                                .orElseThrow(
                                                () -> new ResourceNotFoundException(
                                                                "Reservation with ID "
                                                                                + id
                                                                                + " not found"));
        }

        // =========================================================
        // VALIDATION
        // =========================================================

        private void validateReservationRequest(
                        ReservationRequest request) {

                if (request == null) {
                        throw new IllegalArgumentException(
                                        "Reservation request cannot be null");
                }

                if (request.getType() == null
                                || request.getType().isBlank()) {
                        throw new IllegalArgumentException(
                                        "Reservation type is required");
                }

                if (request.getPassengers() == null
                                || request.getPassengers().isEmpty()) {
                        throw new IllegalArgumentException(
                                        "At least one passenger is required");
                }
        }
}