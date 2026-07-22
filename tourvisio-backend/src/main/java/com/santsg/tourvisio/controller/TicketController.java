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

@RestController
@RequestMapping("/api/tickets")
@Tag(name = "Ticket Controller", description = "Endpoints for purchasing flight tickets in PostgreSQL")
public class TicketController {

    private final ReservationService reservationService;

    public TicketController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    @Operation(summary = "Create a flight ticket", description = "Creates a flight ticket reservation and triggers asynchronous email delivery.")
    public ResponseEntity<Reservation> createTicket(
            @Valid @RequestBody ReservationRequest request,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (request.getType() == null || request.getType().isBlank()) {
            request.setType("FLIGHT");
        }
        Reservation created = reservationService.createReservation(request, userId);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }
}
