package com.event.event_ticket_app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.event.event_ticket_app.dto.reservation.CreateReservationRequest;
import com.event.event_ticket_app.dto.reservation.ReservationResponse;
import com.event.event_ticket_app.entity.Reservation;
import com.event.event_ticket_app.entity.ReservationStatus;
import com.event.event_ticket_app.entity.Role;
import com.event.event_ticket_app.entity.User;
import com.event.event_ticket_app.exception.ConflictException;
import com.event.event_ticket_app.exception.ForbiddenException;
import com.event.event_ticket_app.repository.ReservationRepository;
import com.event.event_ticket_app.security.UserPrincipal;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationWriter reservationWriter;

    @Mock
    private IdempotencyService idempotencyService;

    private ReservationService reservationService;

    private UserPrincipal customer;

    @BeforeEach
    void setUp() {
        reservationService = new ReservationService(reservationRepository, reservationWriter, idempotencyService);

        User user = new User();
        user.setId(1L);
        user.setEmail("alperen@deneme.com");
        user.setPasswordHash("hashed");
        user.setRoles(Set.of(Role.CUSTOMER));
        customer = new UserPrincipal(user);
    }

    @Test
    void createReturnsCachedResult() {
        when(idempotencyService.hash(anyString())).thenReturn("hash");
        when(idempotencyService.start(eq("key-1"), eq(1L), anyString(), eq("hash"))).thenReturn(Optional.of(55L));
        when(reservationRepository.findById(55L)).thenReturn(Optional.of(existingReservation(55L)));

        ReservationResponse response = reservationService.create(
                1L, customer, new CreateReservationRequest(2), "key-1");

        assertThat(response.id()).isEqualTo(55L);
        verify(reservationWriter, never()).write(anyLong(), anyLong(), anyInt());
    }

    @Test
    void createCompletesOnSuccess() {
        when(idempotencyService.hash(anyString())).thenReturn("hash");
        when(idempotencyService.start(eq("key-2"), eq(1L), anyString(), eq("hash"))).thenReturn(Optional.empty());
        when(reservationWriter.write(1L, 1L, 2)).thenReturn(existingReservation(77L));

        ReservationResponse response = reservationService.create(
                1L, customer, new CreateReservationRequest(2), "key-2");

        assertThat(response.id()).isEqualTo(77L);
        verify(idempotencyService).complete("key-2", 1L, 77L);
    }

    @Test
    void createMarksFailedOnError() {
        when(idempotencyService.hash(anyString())).thenReturn("hash");
        when(idempotencyService.start(eq("key-3"), eq(1L), anyString(), eq("hash"))).thenReturn(Optional.empty());
        when(reservationWriter.write(1L, 1L, 2)).thenThrow(new ConflictException("Not enough seats available"));

        CreateReservationRequest request = new CreateReservationRequest(2);

        assertThatThrownBy(() -> reservationService.create(1L, customer, request, "key-3"))
                .isInstanceOf(ConflictException.class);

        verify(idempotencyService).fail("key-3", 1L);
    }

    @Test
    void confirmRejectsNonOwner() {
        Reservation reservation = existingReservation(1L);
        reservation.setUserId(999L);
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.confirm(1L, customer))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void confirmRejectsNonPending() {
        Reservation reservation = existingReservation(1L);
        reservation.setStatus(ReservationStatus.CANCELLED);
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.confirm(1L, customer))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void cancelRejectsCancelled() {
        Reservation reservation = existingReservation(1L);
        reservation.setStatus(ReservationStatus.CANCELLED);
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.cancel(1L, customer))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void cancelSucceedsForOwner() {
        Reservation reservation = existingReservation(1L);
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponse response = reservationService.cancel(1L, customer);

        assertThat(response.status()).isEqualTo(ReservationStatus.CANCELLED);
    }

    private Reservation existingReservation(Long id) {
        Reservation reservation = new Reservation();
        reservation.setId(id);
        reservation.setEventId(1L);
        reservation.setUserId(1L);
        reservation.setSeats(2);
        return reservation;
    }
}
