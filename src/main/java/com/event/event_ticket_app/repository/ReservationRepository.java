package com.event.event_ticket_app.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.event.event_ticket_app.entity.Reservation;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByEventId(Long eventId);

    List<Reservation> findByUserId(Long userId);

    @Query("""
            SELECT COALESCE(SUM(r.seats), 0) FROM Reservation r
            WHERE r.eventId = :eventId AND r.status <> com.event.event_ticket_app.entity.ReservationStatus.CANCELLED
            """)
    int sumActiveSeats(@Param("eventId") Long eventId);
}
