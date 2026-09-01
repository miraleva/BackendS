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
import com.santsg.tourvisio.chat.SearchCriteria;
import com.santsg.tourvisio.chat.ChatSessionStore;
import java.time.LocalDate;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.santsg.tourvisio.client.TourVisioBookingApiClient;
import com.santsg.tourvisio.dto.tourvisio.TourVisioBookingRequest;
import com.santsg.tourvisio.dto.tourvisio.TourVisioBookingResponse;
import com.santsg.tourvisio.exception.TourVisioApiException;
import org.springframework.beans.factory.annotation.Autowired;

@Service
@Slf4j
public class ReservationService {

        private final ReservationRepository reservationRepository;
        private final EmailService emailService;
        private final UserRepository userRepository;
        private final NotificationRepository notificationRepository;
        private final TourVisioBookingApiClient tourVisioBookingApiClient;
        private final ChatSessionStore chatSessionStore;

        @Autowired
        public ReservationService(
                        ReservationRepository reservationRepository,
                        EmailService emailService,
                        UserRepository userRepository,
                        NotificationRepository notificationRepository,
                        @Autowired(required = false) TourVisioBookingApiClient tourVisioBookingApiClient,
                        @Autowired(required = false) ChatSessionStore chatSessionStore) {
                this.reservationRepository = reservationRepository;
                this.emailService = emailService;
                this.userRepository = userRepository;
                this.notificationRepository = notificationRepository;
                this.tourVisioBookingApiClient = tourVisioBookingApiClient;
                this.chatSessionStore = chatSessionStore;
        }

        public ReservationService(
                        ReservationRepository reservationRepository,
                        EmailService emailService,
                        UserRepository userRepository,
                        NotificationRepository notificationRepository) {
                this(reservationRepository, emailService, userRepository, notificationRepository, null, null);
        }

        public ReservationService(
                        ReservationRepository reservationRepository,
                        EmailService emailService) {
                this(reservationRepository, emailService, null, null, null, null);
        }

        // =========================================================
        // CREATE RESERVATION
        // =========================================================

        @Transactional
        public Reservation createReservation(
                        ReservationRequest request,
                        Long userId) {

                if (request.getChatSessionId() != null && chatSessionStore != null) {
                        try {
                                SearchCriteria criteria = chatSessionStore.getOrCreate(request.getChatSessionId());
                                if (criteria != null) {
                                        autoFillPassengerBirthDates(request, criteria);
                                        if (request.getDepartureCity() == null && criteria.getDepartureLocation() != null) {
                                                request.setDepartureCity(criteria.getDepartureLocation());
                                        }
                                        if (request.getArrivalCity() == null && criteria.getArrivalLocation() != null) {
                                                request.setArrivalCity(criteria.getArrivalLocation());
                                        }
                                }
                        } catch (Exception e) {
                                log.error("[ReservationService] Failed to auto-fill details from chatSessionId=" + request.getChatSessionId(), e);
                        }
                }

                validateReservationRequest(request);

                log.info(
                                "[ReservationService] createReservation başladı. userId={}",
                                userId);

                User user = null;

                if (userId != null && userRepository != null) {
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

                // TourVisio GDS API booking call (or fallback PNR generation)
                String reservationNum;
                if (tourVisioBookingApiClient != null) {
                        TourVisioBookingRequest bookingReq = TourVisioBookingRequest.fromReservationRequest(request);
                        TourVisioBookingResponse bookingResp = tourVisioBookingApiClient.makeBooking(bookingReq);
                        if (!bookingResp.isSuccess()) {
                                throw new TourVisioApiException(bookingResp.getMessage());
                        }
                        reservationNum = bookingResp.getReservationNumber();
                } else {
                        reservationNum = "TV-" + (100000 + new Random().nextInt(900000));
                }

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
                        if (notificationRepository != null) {
                                Notification savedNotification = notificationRepository.save(notification);

                                log.info(
                                                "[ReservationService] BİLDİRİM OLUŞTURULDU. notificationId={}, userId={}, type={}, title={}",
                                                savedNotification.getId(),
                                                user.getId(),
                                                savedNotification.getType(),
                                                savedNotification.getTitle());
                        }
                } catch (Exception e) {
                        log.error(
                                        "[ReservationService] BİLDİRİM KAYDEDİLEMEDİ. userId={}, reservationId={}, hata={}",
                                        user.getId(),
                                        reservation.getId(),
                                        e.getMessage(),
                                        e);
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

        private void autoFillPassengerBirthDates(ReservationRequest request, SearchCriteria criteria) {
                if (request.getPassengers() == null || request.getPassengers().isEmpty()) {
                        return;
                }

                int adultCount = criteria.getAdultCount() != null ? criteria.getAdultCount() : 1;
                int childCount = criteria.getChildCount() != null ? criteria.getChildCount() : 0;
                int infantCount = criteria.getInfantCount() != null ? criteria.getInfantCount() : 0;

                List<Integer> childAges = criteria.getChildAges() != null ? criteria.getChildAges() : new ArrayList<>();
                List<Integer> infantMonths = criteria.getInfantAgesInMonths() != null ? criteria.getInfantAgesInMonths() : new ArrayList<>();

                // Safe defaults if age lists are shorter than count
                while (childAges.size() < childCount) {
                        childAges.add(6); // default 6 years old
                }
                while (infantMonths.size() < infantCount) {
                        infantMonths.add(6); // default 6 months old
                }

                LocalDate tripStart = request.getStartDate() != null ? request.getStartDate() : LocalDate.now();

                List<PassengerRequest> passengers = request.getPassengers();
                for (int i = 0; i < passengers.size(); i++) {
                        PassengerRequest pr = passengers.get(i);
                        String genderUpper = pr.getGender() != null ? pr.getGender().toUpperCase() : "";

                        boolean isInfant = genderUpper.equals("INF") || genderUpper.equals("INFANT");
                        boolean isChild = genderUpper.equals("CHD") || genderUpper.equals("CHILD");

                        // If gender/title is not explicit, use index order classification
                        if (!isInfant && !isChild) {
                                int childStartIndex = adultCount;
                                int infantStartIndex = adultCount + childCount;

                                if (i >= infantStartIndex && i < infantStartIndex + infantCount) {
                                        isInfant = true;
                                } else if (i >= childStartIndex && i < childStartIndex + childCount) {
                                        isChild = true;
                                }
                        }

                        if (isInfant) {
                                int infantIdx = 0;
                                // If using index order, calculate index relative to infant start
                                if (i >= (adultCount + childCount)) {
                                        infantIdx = i - (adultCount + childCount);
                                }
                                int months = 6; // default
                                if (infantIdx < infantMonths.size()) {
                                        months = infantMonths.get(infantIdx);
                                }
                                // Set birth date so they are exactly 'months' old at trip start
                                pr.setBirthDate(tripStart.minusMonths(months));
                                // Set gender to INF if not set
                                if (pr.getGender() == null || pr.getGender().isBlank() || pr.getGender().equals("MR") || pr.getGender().equals("MRS")) {
                                        pr.setGender("INF");
                                }
                                log.info("[ReservationService] Auto-filled infant birthdate for passenger {}: {}, age = {} months", i, pr.getBirthDate(), months);
                        } else if (isChild) {
                                int childIdx = 0;
                                if (i >= adultCount) {
                                        childIdx = i - adultCount;
                                }
                                int years = 6; // default
                                if (childIdx < childAges.size()) {
                                        years = childAges.get(childIdx);
                                }
                                pr.setBirthDate(tripStart.minusYears(years));
                                if (pr.getGender() == null || pr.getGender().isBlank() || pr.getGender().equals("MR") || pr.getGender().equals("MRS")) {
                                        pr.setGender("CHD");
                                }
                                log.info("[ReservationService] Auto-filled child birthdate for passenger {}: {}, age = {} years", i, pr.getBirthDate(), years);
                        }
                }
        }
}