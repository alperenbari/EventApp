package com.event.event_ticket_app.dto.reservation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateReservationRequest(@NotNull @Positive Integer seats) {
}
