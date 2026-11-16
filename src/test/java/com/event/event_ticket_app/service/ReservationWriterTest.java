package com.event.event_ticket_app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.event.event_ticket_app.entity.Event;
import com.event.event_ticket_app.entity.Reservation;
import com.event.event_ticket_app.exception.ConflictException;
import com.event.event_ticket_app.exception.NotFoundException;
import com.event.event_ticket_app.repository.EventRepository;
import com.event.event_ticket_app.repository.ReservationRepository;

@ExtendWith(MockitoExtension.class)
class ReservationWriterTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @InjectMocks
    private ReservationWriter reservationWriter;

    @Test
    void throwsWhenEventMissing() {
        when(eventRepository.findWithLockById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationWriter.write(1L, 10L, 2))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectsUnpublishedEvent() {
        Event event = publishedEvent(10, false);
        when(eventRepository.findWithLockById(1L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> reservationWriter.write(1L, 10L, 2))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsWhenNoSeatsLeft() {
        Event event = publishedEvent(10, true);
        when(eventRepository.findWithLockById(1L)).thenReturn(Optional.of(event));
        when(reservationRepository.sumActiveSeats(1L)).thenReturn(9);

        assertThatThrownBy(() -> reservationWriter.write(1L, 10L, 2))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createsReservationWhenCapacityAllows() {
        Event event = publishedEvent(10, true);
        when(eventRepository.findWithLockById(1L)).thenReturn(Optional.of(event));
        when(reservationRepository.sumActiveSeats(1L)).thenReturn(5);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Reservation reservation = reservationWriter.write(1L, 10L, 2);

        assertThat(reservation.getEventId()).isEqualTo(1L);
        assertThat(reservation.getUserId()).isEqualTo(10L);
        assertThat(reservation.getSeats()).isEqualTo(2);
    }

    private Event publishedEvent(int capacity, boolean published) {
        Event event = new Event();
        event.setId(1L);
        event.setOwnerId(99L);
        event.setCapacity(capacity);
        event.setPublished(published);
        return event;
    }
}
