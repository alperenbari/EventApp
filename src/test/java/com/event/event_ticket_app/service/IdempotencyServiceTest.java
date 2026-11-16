package com.event.event_ticket_app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.event.event_ticket_app.entity.IdempotencyKey;
import com.event.event_ticket_app.entity.IdempotencyStatus;
import com.event.event_ticket_app.exception.ConflictException;
import com.event.event_ticket_app.repository.IdempotencyKeyRepository;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private IdempotencyKeyRepository repository;

    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        idempotencyService = new IdempotencyService(repository);
    }

    @Test
    void startCreatesNewRecord() {
        when(repository.findByKeyAndUserId("key-1", USER_ID)).thenReturn(Optional.empty());

        Optional<Long> result = idempotencyService.start("key-1", USER_ID, "POST /x", "hash-1");

        assertThat(result).isEmpty();

        ArgumentCaptor<IdempotencyKey> captor = ArgumentCaptor.forClass(IdempotencyKey.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(IdempotencyStatus.IN_PROGRESS);
        assertThat(captor.getValue().getEndpoint()).isEqualTo("POST /x");
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
    }

    @Test
    void startRejectsConcurrentInsertRace() {
        when(repository.findByKeyAndUserId("key-1", USER_ID)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(IdempotencyKey.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> idempotencyService.start("key-1", USER_ID, "POST /x", "hash-1"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void startReturnsCachedId() {
        IdempotencyKey existing = completedRecord("key-2", "POST /x", "hash-2", "77");
        when(repository.findByKeyAndUserId("key-2", USER_ID)).thenReturn(Optional.of(existing));

        Optional<Long> result = idempotencyService.start("key-2", USER_ID, "POST /x", "hash-2");

        assertThat(result).contains(77L);
    }

    @Test
    void startRejectsDifferentRequest() {
        IdempotencyKey existing = completedRecord("key-3", "POST /x", "hash-original", "1");
        when(repository.findByKeyAndUserId("key-3", USER_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> idempotencyService.start("key-3", USER_ID, "POST /x", "hash-different"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void startRejectsInProgress() {
        IdempotencyKey existing = new IdempotencyKey();
        existing.setKey("key-4");
        existing.setEndpoint("POST /x");
        existing.setRequestHash("hash-4");
        existing.setStatus(IdempotencyStatus.IN_PROGRESS);
        when(repository.findByKeyAndUserId("key-4", USER_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> idempotencyService.start("key-4", USER_ID, "POST /x", "hash-4"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void startAllowsRetry() {
        IdempotencyKey existing = new IdempotencyKey();
        existing.setKey("key-5");
        existing.setEndpoint("POST /x");
        existing.setRequestHash("hash-5");
        existing.setStatus(IdempotencyStatus.FAILED);
        when(repository.findByKeyAndUserId("key-5", USER_ID)).thenReturn(Optional.of(existing));

        Optional<Long> result = idempotencyService.start("key-5", USER_ID, "POST /x", "hash-5");

        assertThat(result).isEmpty();
        assertThat(existing.getStatus()).isEqualTo(IdempotencyStatus.IN_PROGRESS);
    }

    @Test
    void completeStoresId() {
        IdempotencyKey existing = new IdempotencyKey();
        existing.setKey("key-6");
        existing.setStatus(IdempotencyStatus.IN_PROGRESS);
        when(repository.findByKeyAndUserId("key-6", USER_ID)).thenReturn(Optional.of(existing));

        idempotencyService.complete("key-6", USER_ID, 123L);

        assertThat(existing.getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
        assertThat(existing.getResponseHash()).isEqualTo("123");
    }

    @Test
    void hashIsDeterministic() {
        String first = idempotencyService.hash("payload-a");
        String second = idempotencyService.hash("payload-a");
        String third = idempotencyService.hash("payload-b");

        assertThat(first).isEqualTo(second);
        assertThat(first).isNotEqualTo(third);
    }

    private IdempotencyKey completedRecord(String key, String endpoint, String requestHash, String responseHash) {
        IdempotencyKey record = new IdempotencyKey();
        record.setKey(key);
        record.setEndpoint(endpoint);
        record.setRequestHash(requestHash);
        record.setResponseHash(responseHash);
        record.setStatus(IdempotencyStatus.COMPLETED);
        return record;
    }
}
