package com.event.event_ticket_app.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.event.event_ticket_app.dto.reservation.CreateReservationRequest;
import com.event.event_ticket_app.dto.reservation.ReservationResponse;
import com.event.event_ticket_app.entity.Event;
import com.event.event_ticket_app.entity.Reservation;
import com.event.event_ticket_app.entity.ReservationStatus;
import com.event.event_ticket_app.entity.Role;
import com.event.event_ticket_app.entity.User;
import com.event.event_ticket_app.repository.EventRepository;
import com.event.event_ticket_app.repository.ReservationRepository;
import com.event.event_ticket_app.repository.UserRepository;
import com.event.event_ticket_app.security.UserPrincipal;
import com.event.event_ticket_app.service.ReservationService;

@SpringBootTest
class ReservationConcurrencyTest {

    private static final int CAPACITY = 5;
    private static final int CONCURRENT_REQUESTS = 15;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Test
    void reservationsStayUnderCapacity() throws Exception {
        Event event = publishEvent(CAPACITY);
        UserPrincipal caller = customerPrincipal();

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CountDownLatch startSignal = new CountDownLatch(1);

        List<Callable<ReservationResponse>> tasks = java.util.stream.IntStream.range(0, CONCURRENT_REQUESTS)
                .mapToObj(i -> (Callable<ReservationResponse>) () -> {
                    startSignal.await();
                    String idempotencyKey = UUID.randomUUID().toString();
                    CreateReservationRequest request = new CreateReservationRequest(1);
                    return reservationService.create(event.getId(), caller, request, idempotencyKey);
                })
                .toList();

        List<Future<ReservationResponse>> futures = tasks.stream().map(executor::submit).toList();
        startSignal.countDown();

        int succeeded = 0;
        int rejected = 0;
        for (Future<ReservationResponse> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
                succeeded++;
            } catch (Exception ex) {
                rejected++;
            }
        }
        executor.shutdown();

        List<Reservation> reservations = reservationRepository.findByEventId(event.getId());
        int totalReservedSeats = reservations.stream()
                .filter(r -> r.getStatus() != ReservationStatus.CANCELLED)
                .mapToInt(Reservation::getSeats)
                .sum();

        assertThat(totalReservedSeats).isLessThanOrEqualTo(CAPACITY);
        assertThat(succeeded).isEqualTo(CAPACITY);
        assertThat(succeeded + rejected).isEqualTo(CONCURRENT_REQUESTS);
    }

    private Event publishEvent(int capacity) {
        Event event = new Event();
        event.setOwnerId(1L);
        event.setTitle("Concurrency Test Event");
        event.setVenue("Test Hall");
        event.setStartsAt(Instant.now().plus(1, ChronoUnit.DAYS));
        event.setEndsAt(Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS));
        event.setCapacity(capacity);
        event.setPublished(true);
        return eventRepository.save(event);
    }

    private UserPrincipal customerPrincipal() {
        User user = new User();
        user.setEmail("alperen+" + UUID.randomUUID() + "@deneme.com");
        user.setPasswordHash("hashed");
        user.setRoles(Set.of(Role.CUSTOMER));
        return new UserPrincipal(userRepository.save(user));
    }
}
