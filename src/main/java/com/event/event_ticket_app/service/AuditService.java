package com.event.event_ticket_app.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.event.event_ticket_app.entity.AuditLog;
import com.event.event_ticket_app.repository.AuditLogRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long actorId, String action, String resourceType, Long resourceId) {
        AuditLog entry = new AuditLog();
        entry.setActorId(actorId);
        entry.setAction(action);
        entry.setResourceType(resourceType);
        entry.setResourceId(resourceId);

        HttpServletRequest request = currentRequest();
        if (request != null) {
            entry.setIp(request.getRemoteAddr());
            entry.setUserAgent(request.getHeader("User-Agent"));
        }

        auditLogRepository.save(entry);
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
