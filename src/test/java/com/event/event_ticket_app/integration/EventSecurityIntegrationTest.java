package com.event.event_ticket_app.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.event.event_ticket_app.dto.event.CreateEventRequest;
import com.event.event_ticket_app.entity.Role;
import com.event.event_ticket_app.entity.User;
import com.event.event_ticket_app.repository.UserRepository;
import com.event.event_ticket_app.security.JwtService;
import com.event.event_ticket_app.security.UserPrincipal;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EventSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private String organizerAToken;
    private String organizerBToken;
    private String adminToken;
    private String customerToken;

    @BeforeEach
    void setUp() {
        organizerAToken = tokenFor("organizer-a@example.com", Role.ORGANIZER);
        organizerBToken = tokenFor("organizer-b@example.com", Role.ORGANIZER);
        adminToken = tokenFor("admin@example.com", Role.ADMIN);
        customerToken = tokenFor("customer@example.com", Role.CUSTOMER);
    }

    @Test
    void customerCannotCreateEvent() throws Exception {
        mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizerCanCreateEvent() throws Exception {
        mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + organizerAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.published").value(false));
    }

    @Test
    void organizerCannotUpdateOthers() throws Exception {
        Long eventId = createEventAs(organizerAToken);

        mockMvc.perform(put("/api/events/" + eventId)
                        .header("Authorization", "Bearer " + organizerBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanUpdateAnyEvent() throws Exception {
        Long eventId = createEventAs(organizerAToken);

        mockMvc.perform(put("/api/events/" + eventId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk());
    }

    @Test
    void ownerCanPublishEvent() throws Exception {
        Long eventId = createEventAs(organizerAToken);

        mockMvc.perform(post("/api/events/" + eventId + "/publish")
                        .header("Authorization", "Bearer " + organizerAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(true));
    }

    private Long createEventAs(String token) throws Exception {
        String response = mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("id").asLong();
    }

    private CreateEventRequest validRequest() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        return new CreateEventRequest("Conference", "Main Hall", start, start.plus(2, ChronoUnit.HOURS), 100);
    }

    private String tokenFor(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setRoles(Set.of(role));
        user = userRepository.save(user);

        return jwtService.generateAccessToken(new UserPrincipal(user));
    }
}
