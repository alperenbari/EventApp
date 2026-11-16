package com.event.event_ticket_app.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long actorId;

    @Column(nullable = false)
    @NotBlank
    private String action;

    @Column(nullable = false)
    @NotBlank
    private String resourceType;

    @Column(nullable = false)
    private Long resourceId;

    @Column
    private String ip;

    @Column
    private String userAgent;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

}
