package com.santsg.tourvisio.controller;

import com.santsg.tourvisio.dto.ReservationRequest;
import com.santsg.tourvisio.entity.Reservation;
import com.santsg.tourvisio.service.ReservationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reservations")
@Tag(name = "Reservation Controller", description = "Endpoints for creating and retrieving booking records in PostgreSQL")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(
            ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    // =========================================================
    // CREATE RESERVATION
    // =========================================================

    @PostMapping
    @Operation(summary = "Create a new reservation", description = "Creates a reservation for the logged-in user and generates notifications when enabled.")
    public ResponseEntity<Reservation> createReservation(
            @RequestAttribute(value = "userId", required = false) Long userId,

            @Valid @RequestBody ReservationRequest request) {

        Reservation created = reservationService.createReservation(
                request,
                userId);

        return new ResponseEntity<>(
                created,
                HttpStatus.CREATED);
    }

    // =========================================================
    // GET ALL
    // =========================================================

    @GetMapping
    @Operation(summary = "Get all reservations", description = "Retrieves all hotel and flight bookings.")
    public ResponseEntity<List<Reservation>> getAllReservations() {

        List<Reservation> list = reservationService.getAllReservations();

        return ResponseEntity.ok(list);
    }

    // =========================================================
    // GET BY ID
    // =========================================================

    @GetMapping("/{id:\\d+}")
    @Operation(summary = "Get reservation by ID", description = "Retrieves a specific booking by its numeric ID.")
    public ResponseEntity<Reservation> getReservationById(
            @PathVariable Long id) {

        Reservation reservation = reservationService.getReservationById(id);

        return ResponseEntity.ok(reservation);
    }
}