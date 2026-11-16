package com.event.event_ticket_app.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.event.event_ticket_app.entity.IdempotencyKey;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

    Optional<IdempotencyKey> findByKeyAndUserId(String key, Long userId);

    void deleteByCreatedAtBefore(Instant cutoff);
}
