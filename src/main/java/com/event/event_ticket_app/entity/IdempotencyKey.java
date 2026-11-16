package com.event.event_ticket_app.entity;

import java.time.Duration;
import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"key_name", "userId"}))
@Getter @Setter @NoArgsConstructor
public class IdempotencyKey {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "key_name", nullable = false)
    @NotBlank
    private String key;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    @NotBlank
    private String endpoint;

    @Column(nullable = false)
    private String requestHash;

    @Column
    private String responseHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IdempotencyStatus status = IdempotencyStatus.IN_PROGRESS;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Duration ttl;



    
    
}
