package com.event.event_ticket_app.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.event.event_ticket_app.entity.Event;
import com.event.event_ticket_app.entity.Reservation;
import com.event.event_ticket_app.exception.ConflictException;
import com.event.event_ticket_app.exception.NotFoundException;
import com.event.event_ticket_app.repository.EventRepository;
import com.event.event_ticket_app.repository.ReservationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReservationWriter {

    private final EventRepository eventRepository;
    private final ReservationRepository reservationRepository;

    @Transactional
    public Reservation write(Long eventId, Long userId, int seats) {
        Event event = eventRepository.findWithLockById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventId));

        if (!event.isPublished()) {
            throw new ConflictException("Cannot reserve seats for an unpublished event");
        }

        int activeSeats = reservationRepository.sumActiveSeats(eventId);
        if (activeSeats + seats > event.getCapacity()) {
            throw new ConflictException("Not enough seats available for this event");
        }

        Reservation reservation = new Reservation();
        reservation.setEventId(eventId);
        reservation.setUserId(userId);
        reservation.setSeats(seats);

        return reservationRepository.save(reservation);
    }
}
