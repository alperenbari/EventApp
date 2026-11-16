package com.event.event_ticket_app.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.event.event_ticket_app.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
