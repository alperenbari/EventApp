package com.event.event_ticket_app.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.event.event_ticket_app.entity.IdempotencyKey;
import com.event.event_ticket_app.entity.IdempotencyStatus;
import com.event.event_ticket_app.exception.ConflictException;
import com.event.event_ticket_app.repository.IdempotencyKeyRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    private final IdempotencyKeyRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Long> start(String key, Long userId, String endpoint, String requestHash) {
        Optional<IdempotencyKey> existing = repository.findByKeyAndUserId(key, userId);

        if (existing.isEmpty()) {
            IdempotencyKey record = new IdempotencyKey();
            record.setKey(key);
            record.setUserId(userId);
            record.setEndpoint(endpoint);
            record.setRequestHash(requestHash);
            record.setTtl(DEFAULT_TTL);
            try {
                repository.saveAndFlush(record);
            } catch (DataIntegrityViolationException ex) {
                // two concurrent requests both found no existing row and raced to insert one;
                // the one that loses the unique-constraint race just treats it as in-progress.
                throw new ConflictException("A request with this Idempotency-Key is already being processed");
            }
            return Optional.empty();
        }

        IdempotencyKey record = existing.get();

        if (!record.getEndpoint().equals(endpoint) || !record.getRequestHash().equals(requestHash)) {
            throw new ConflictException("This Idempotency-Key was already used with a different request");
        }

        if (record.getStatus() == IdempotencyStatus.IN_PROGRESS) {
            throw new ConflictException("A request with this Idempotency-Key is already being processed");
        }

        if (record.getStatus() == IdempotencyStatus.FAILED) {
            record.setStatus(IdempotencyStatus.IN_PROGRESS);
            repository.save(record);
            return Optional.empty();
        }

        return Optional.of(Long.valueOf(record.getResponseHash()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String key, Long userId, Long resourceId) {
        repository.findByKeyAndUserId(key, userId).ifPresent(record -> {
            record.setResponseHash(String.valueOf(resourceId));
            record.setStatus(IdempotencyStatus.COMPLETED);
            repository.save(record);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(String key, Long userId) {
        repository.findByKeyAndUserId(key, userId).ifPresent(record -> {
            record.setStatus(IdempotencyStatus.FAILED);
            repository.save(record);
        });
    }

    @Scheduled(fixedRate = 3_600_000)
    @Transactional
    public void evictExpiredKeys() {
        repository.deleteByCreatedAtBefore(Instant.now().minus(DEFAULT_TTL));
    }

    public String hash(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
