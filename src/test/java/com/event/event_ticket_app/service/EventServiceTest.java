package com.event.event_ticket_app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.event.event_ticket_app.dto.event.CreateEventRequest;
import com.event.event_ticket_app.dto.event.EventResponse;
import com.event.event_ticket_app.entity.Event;
import com.event.event_ticket_app.entity.Role;
import com.event.event_ticket_app.entity.User;
import com.event.event_ticket_app.exception.ForbiddenException;
import com.event.event_ticket_app.exception.NotFoundException;
import com.event.event_ticket_app.repository.EventRepository;
import com.event.event_ticket_app.security.UserPrincipal;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private EventService eventService;

    private UserPrincipal organizer;
    private UserPrincipal admin;

    @BeforeEach
    void setUp() {
        organizer = principalWithId(1L, Role.ORGANIZER);
        admin = principalWithId(99L, Role.ADMIN);
    }

    @Test
    void createDraftSetsOwner() {
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateEventRequest request = validRequest();
        EventResponse response = eventService.createDraft(organizer, request);

        assertThat(response.ownerId()).isEqualTo(1L);
        assertThat(response.published()).isFalse();
    }

    @Test
    void createDraftRejectsBadDates() {
        Instant start = Instant.now();
        CreateEventRequest invalid = new CreateEventRequest("Title", "Venue", start, start.minus(1, ChronoUnit.HOURS), 10);

        assertThatThrownBy(() -> eventService.createDraft(organizer, invalid))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateMissingEventFails() {
        when(eventRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.update(42L, organizer, validRequest()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateRejectsNonOwner() {
        Event event = existingEventOwnedBy(1L);
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        UserPrincipal otherOrganizer = principalWithId(2L, Role.ORGANIZER);

        assertThatThrownBy(() -> eventService.update(event.getId(), otherOrganizer, validRequest()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updateAllowsAdmin() {
        Event event = existingEventOwnedBy(1L);
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EventResponse response = eventService.update(event.getId(), admin, validRequest());

        assertThat(response.ownerId()).isEqualTo(1L);
    }

    @Test
    void publishSetsPublished() {
        Event event = existingEventOwnedBy(1L);
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EventResponse response = eventService.publish(event.getId(), organizer);

        assertThat(response.published()).isTrue();
    }

    @Test
    void listIgnoresFilterForUser() {
        when(eventRepository.findByOwnerId(1L)).thenReturn(List.of(existingEventOwnedBy(1L)));

        List<EventResponse> result = eventService.list(999L, organizer);

        assertThat(result).hasSize(1);
        verify(eventRepository).findByOwnerId(1L);
    }

    @Test
    void listUsesFilterForAdmin() {
        when(eventRepository.findByOwnerId(5L)).thenReturn(List.of(existingEventOwnedBy(5L)));

        List<EventResponse> result = eventService.list(5L, admin);

        assertThat(result).hasSize(1);
    }

    private Event existingEventOwnedBy(Long ownerId) {
        Event event = new Event();
        event.setId(1L);
        event.setOwnerId(ownerId);
        event.setTitle("Original title");
        event.setVenue("Original venue");
        event.setStartsAt(Instant.now());
        event.setEndsAt(Instant.now().plus(2, ChronoUnit.HOURS));
        event.setCapacity(100);
        return event;
    }

    private CreateEventRequest validRequest() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        return new CreateEventRequest("Conference", "Main Hall", start, start.plus(2, ChronoUnit.HOURS), 100);
    }

    private UserPrincipal principalWithId(Long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setEmail(id + "@example.com");
        user.setPasswordHash("hashed");
        user.setRoles(Set.of(role));
        return new UserPrincipal(user);
    }
}
