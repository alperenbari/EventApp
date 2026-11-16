package com.event.event_ticket_app.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.event.event_ticket_app.dto.reservation.CreateReservationRequest;
import com.event.event_ticket_app.dto.reservation.ReservationResponse;
import com.event.event_ticket_app.security.UserPrincipal;
import com.event.event_ticket_app.service.ReservationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping("/api/events/{eventId}/reservations")
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER')")
    public ResponseEntity<ReservationResponse> create(
            @PathVariable Long eventId,
            @AuthenticationPrincipal UserPrincipal caller,
            @Valid @RequestBody CreateReservationRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        ReservationResponse response = reservationService.create(eventId, caller, request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/api/reservations/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER')")
    public ResponseEntity<ReservationResponse> confirm(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal caller) {
        return ResponseEntity.ok(reservationService.confirm(id, caller));
    }

    @PostMapping("/api/reservations/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER')")
    public ResponseEntity<ReservationResponse> cancel(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal caller) {
        return ResponseEntity.ok(reservationService.cancel(id, caller));
    }
}
