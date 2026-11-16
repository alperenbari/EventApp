package com.event.event_ticket_app.controller;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.event.event_ticket_app.dto.event.CreateEventRequest;
import com.event.event_ticket_app.dto.event.EventResponse;
import com.event.event_ticket_app.security.UserPrincipal;
import com.event.event_ticket_app.service.EventService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public ResponseEntity<EventResponse> create(
            @AuthenticationPrincipal UserPrincipal caller,
            @Valid @RequestBody CreateEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.createDraft(caller, request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public ResponseEntity<EventResponse> update(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal caller,
            @Valid @RequestBody CreateEventRequest request) {
        return ResponseEntity.ok(eventService.update(id, caller, request));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public ResponseEntity<EventResponse> publish(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal caller) {
        return ResponseEntity.ok(eventService.publish(id, caller));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public ResponseEntity<List<EventResponse>> list(
            @RequestParam(required = false) Long ownerId,
            @AuthenticationPrincipal UserPrincipal caller) {
        return ResponseEntity.ok(eventService.list(ownerId, caller));
    }

    @GetMapping("/public")
    public ResponseEntity<List<EventResponse>> searchPublic(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(eventService.searchPublic(from, to, q));
    }
}
