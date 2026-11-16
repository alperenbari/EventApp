package com.event.event_ticket_app.service;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.event.event_ticket_app.dto.reservation.CreateReservationRequest;
import com.event.event_ticket_app.dto.reservation.ReservationResponse;
import com.event.event_ticket_app.entity.Reservation;
import com.event.event_ticket_app.entity.ReservationStatus;
import com.event.event_ticket_app.exception.ConflictException;
import com.event.event_ticket_app.exception.ForbiddenException;
import com.event.event_ticket_app.exception.NotFoundException;
import com.event.event_ticket_app.repository.ReservationRepository;
import com.event.event_ticket_app.security.UserPrincipal;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private static final int MAX_ATTEMPTS = 5;

    private final ReservationRepository reservationRepository;
    private final ReservationWriter reservationWriter;
    private final IdempotencyService idempotencyService;

    @Autowired(required = false)
    private AuditService auditService;

    public ReservationResponse create(
            Long eventId, UserPrincipal caller, CreateReservationRequest request, String idempotencyKey) {

        String endpoint = "POST /api/events/" + eventId + "/reservations";
        String requestHash = idempotencyService.hash(eventId + ":" + caller.getId() + ":" + request.seats());

        Optional<Long> alreadyProcessed = idempotencyService.start(idempotencyKey, caller.getId(), endpoint, requestHash);
        if (alreadyProcessed.isPresent()) {
            return ReservationResponse.from(findReservationOrThrow(alreadyProcessed.get()));
        }

        try {
            Reservation reservation = reserveWithRetry(eventId, caller.getId(), request.seats());
            idempotencyService.complete(idempotencyKey, caller.getId(), reservation.getId());
            logAudit(caller.getId(), "CREATE_RESERVATION", reservation.getId());
            return ReservationResponse.from(reservation);
        } catch (RuntimeException ex) {
            idempotencyService.fail(idempotencyKey, caller.getId());
            throw ex;
        }
    }

    @Transactional
    public ReservationResponse confirm(Long reservationId, UserPrincipal caller) {
        Reservation reservation = findReservationOrThrow(reservationId);
        assertOwnerOrAdmin(reservation, caller);

        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new ConflictException("Only pending reservations can be confirmed");
        }

        reservation.setStatus(ReservationStatus.CONFIRMED);
        System.out.println("Rezervasyon onaylandı");
        Reservation saved = reservationRepository.save(reservation);
        logAudit(caller.getId(), "CONFIRM_RESERVATION", saved.getId());
        return ReservationResponse.from(saved);
    }

    @Transactional
    public ReservationResponse cancel(Long reservationId, UserPrincipal caller) {
        Reservation reservation = findReservationOrThrow(reservationId);
        assertOwnerOrAdmin(reservation, caller);

        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new ConflictException("Reservation is already cancelled");
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        Reservation saved = reservationRepository.save(reservation);
        logAudit(caller.getId(), "CANCEL_RESERVATION", saved.getId());
        return ReservationResponse.from(saved);
    }

    private Reservation reserveWithRetry(Long eventId, Long userId, int seats) {
        int attempt = 0;
        while (true) {
            try {
                return reservationWriter.write(eventId, userId, seats);
            } catch (OptimisticLockingFailureException ex) {
                attempt++;
                if (attempt >= MAX_ATTEMPTS) {
                    throw new ConflictException("Event is under heavy load, please retry");
                }
            }
        }
    }

    private void logAudit(Long actorId, String action, Long resourceId) {
        if (auditService != null) {
            auditService.log(actorId, action, "Reservation", resourceId);
        }
    }

    private Reservation findReservationOrThrow(Long reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation not found: " + reservationId));
    }

    private void assertOwnerOrAdmin(Reservation reservation, UserPrincipal caller) {
        boolean isOwner = reservation.getUserId().equals(caller.getId());
        if (!isOwner && !caller.hasRole("ADMIN")) {
            throw new ForbiddenException("You do not own this reservation");
        }
    }
}
