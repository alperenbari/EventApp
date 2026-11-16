package com.event.event_ticket_app.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.event.event_ticket_app.entity.Event;

import jakarta.persistence.LockModeType;

public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByOwnerId(Long ownerId);

    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("SELECT e FROM Event e WHERE e.id = :id")
    Optional<Event> findWithLockById(@Param("id") Long id);

    @Query("""
            SELECT e FROM Event e
            WHERE e.published = true
            AND (:from IS NULL OR e.startsAt >= :from)
            AND (:to IS NULL OR e.endsAt <= :to)
            AND (:q IS NULL OR LOWER(e.title) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    List<Event> searchPublished(@Param("from") Instant from, @Param("to") Instant to, @Param("q") String q);
}
