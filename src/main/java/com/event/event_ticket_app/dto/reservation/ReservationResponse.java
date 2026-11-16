package com.event.event_ticket_app.dto.reservation;

import java.time.Instant;

import com.event.event_ticket_app.entity.Reservation;
import com.event.event_ticket_app.entity.ReservationStatus;

public record ReservationResponse(
        Long id,
        Long eventId,
        Long userId,
        ReservationStatus status,
        Integer seats,
        Instant createdAt) {

    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getEventId(),
                reservation.getUserId(),
                reservation.getStatus(),
                reservation.getSeats(),
                reservation.getCreatedAt());
    }
}
