package com.event.event_ticket_app.dto.event;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateEventRequest(
        @NotBlank String title,
        @NotBlank String venue,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @NotNull @Positive Integer capacity) {
}
