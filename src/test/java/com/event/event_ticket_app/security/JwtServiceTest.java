package com.event.event_ticket_app.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.event.event_ticket_app.entity.Role;
import com.event.event_ticket_app.entity.User;

class JwtServiceTest {

    private static final String SECRET = "e+zkT16Z9EvzSSwuJ69guzrQ/9oXN55K4R+itO9WVxg=";

    private JwtService jwtService;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 900_000L, 604_800_000L);

        User user = new User();
        user.setEmail("alperen@deneme.com");
        user.setPasswordHash("hashed");
        user.setRoles(Set.of(Role.CUSTOMER));
        principal = new UserPrincipal(user);
    }

    @Test
    void accessTokenHasEmail() {
        String token = jwtService.generateAccessToken(principal);

        assertThat(jwtService.extractEmail(token)).isEqualTo("alperen@deneme.com");
        assertThat(jwtService.isRefreshToken(token)).isFalse();
        assertThat(jwtService.isTokenValid(token, "alperen@deneme.com")).isTrue();
    }

    @Test
    void refreshTokenIsMarkedAsRefresh() {
        String token = jwtService.generateRefreshToken(principal);

        assertThat(jwtService.isRefreshToken(token)).isTrue();
    }

    @Test
    void tokenInvalidForOtherEmail() {
        String token = jwtService.generateAccessToken(principal);

        assertThat(jwtService.isTokenValid(token, "someone-else@example.com")).isFalse();
    }

    @Test
    void expiredTokenIsInvalid() throws InterruptedException {
        JwtService shortLivedJwtService = new JwtService(SECRET, 1L, 1L);
        String token = shortLivedJwtService.generateAccessToken(principal);

        Thread.sleep(10);

        assertThat(shortLivedJwtService.isTokenValid(token, "alperen@deneme.com")).isFalse();
    }

    @Test
    void malformedTokenIsInvalid() {
        assertThat(jwtService.isTokenValid("not-a-real-token", "alperen@deneme.com")).isFalse();
    }
}
