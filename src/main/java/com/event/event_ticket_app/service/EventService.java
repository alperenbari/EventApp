package com.event.event_ticket_app.service;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.event.event_ticket_app.dto.event.CreateEventRequest;
import com.event.event_ticket_app.dto.event.EventResponse;
import com.event.event_ticket_app.entity.Event;
import com.event.event_ticket_app.exception.ForbiddenException;
import com.event.event_ticket_app.exception.NotFoundException;
import com.event.event_ticket_app.repository.EventRepository;
import com.event.event_ticket_app.security.UserPrincipal;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;

    @Autowired(required = false)
    private AuditService auditService;

    @Transactional
    public EventResponse createDraft(UserPrincipal caller, CreateEventRequest request) {
        validateDates(request.startsAt(), request.endsAt());

        Event event = new Event();
        event.setOwnerId(caller.getId());
        applyRequest(event, request);

        return EventResponse.from(eventRepository.save(event));
    }

    @Transactional
    public EventResponse update(Long eventId, UserPrincipal caller, CreateEventRequest request) {
        validateDates(request.startsAt(), request.endsAt());

        Event event = findEventOrThrow(eventId);
        assertOwnerOrAdmin(event, caller);

        applyRequest(event, request);

        return EventResponse.from(eventRepository.save(event));
    }

    @Transactional
    public EventResponse publish(Long eventId, UserPrincipal caller) {
        Event event = findEventOrThrow(eventId);
        assertOwnerOrAdmin(event, caller);

        event.setPublished(true);

        System.out.println("Etkinlik yayınlandı");
        Event saved = eventRepository.save(event);
        if (auditService != null) {
            auditService.log(caller.getId(), "PUBLISH_EVENT", "Event", saved.getId());
        }
        return EventResponse.from(saved);
    }

    public List<EventResponse> list(Long ownerIdFilter, UserPrincipal caller) {
        Long effectiveOwnerId = caller.hasRole("ADMIN") ? ownerIdFilter : caller.getId();

        List<Event> events = effectiveOwnerId == null
                ? eventRepository.findAll()
                : eventRepository.findByOwnerId(effectiveOwnerId);

        return events.stream().map(EventResponse::from).toList();
    }

    public List<EventResponse> searchPublic(Instant from, Instant to, String q) {
        return eventRepository.searchPublished(from, to, q).stream()
                .map(EventResponse::from)
                .toList();
    }

    private void applyRequest(Event event, CreateEventRequest request) {
        event.setTitle(request.title());
        event.setVenue(request.venue());
        event.setStartsAt(request.startsAt());
        event.setEndsAt(request.endsAt());
        event.setCapacity(request.capacity());
    }

    private void validateDates(Instant startsAt, Instant endsAt) {
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("endsAt must be after startsAt");
        }
    }

    private Event findEventOrThrow(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventId));
    }

    private void assertOwnerOrAdmin(Event event, UserPrincipal caller) {
        boolean isOwner = event.getOwnerId().equals(caller.getId());
        if (!isOwner && !caller.hasRole("ADMIN")) {
            throw new ForbiddenException("You do not own this event");
        }
    }
}
