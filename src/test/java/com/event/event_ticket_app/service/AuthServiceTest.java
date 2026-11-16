package com.event.event_ticket_app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.event.event_ticket_app.dto.auth.AuthResponse;
import com.event.event_ticket_app.dto.auth.LoginRequest;
import com.event.event_ticket_app.dto.auth.RefreshRequest;
import com.event.event_ticket_app.dto.auth.RegisterRequest;
import com.event.event_ticket_app.dto.auth.UserResponse;
import com.event.event_ticket_app.entity.RefreshToken;
import com.event.event_ticket_app.entity.Role;
import com.event.event_ticket_app.entity.User;
import com.event.event_ticket_app.exception.ConflictException;
import com.event.event_ticket_app.repository.RefreshTokenRepository;
import com.event.event_ticket_app.repository.UserRepository;
import com.event.event_ticket_app.security.JwtService;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, refreshTokenRepository, passwordEncoder, authenticationManager, jwtService);
    }

    @Test
    void registerDuplicateEmailFails() {
        when(userRepository.existsByEmail("alperen@deneme.com")).thenReturn(true);

        RegisterRequest request = new RegisterRequest("alperen@deneme.com", "password123");

        assertThatThrownBy(() -> authService.register(request)).isInstanceOf(ConflictException.class);
    }

    @Test
    void registerSetsCustomerRole() {
        when(userRepository.existsByEmail("alperen@deneme.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        RegisterRequest request = new RegisterRequest("alperen@deneme.com", "password123");
        UserResponse response = authService.register(request);

        assertThat(response.roles()).containsExactly(Role.CUSTOMER);
    }

    @Test
    void refreshRejectsAccessToken() {
        when(jwtService.isRefreshToken("access-token")).thenReturn(false);

        RefreshRequest request = new RefreshRequest("access-token");

        assertThatThrownBy(() -> authService.refresh(request)).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void refreshRejectsBadToken() {
        when(jwtService.isRefreshToken("refresh-token")).thenReturn(true);
        when(jwtService.extractEmail("refresh-token")).thenReturn("alperen@deneme.com");
        when(jwtService.isTokenValid("refresh-token", "alperen@deneme.com")).thenReturn(false);

        RefreshRequest request = new RefreshRequest("refresh-token");

        assertThatThrownBy(() -> authService.refresh(request)).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void refreshRejectsUnknownToken() {
        when(jwtService.isRefreshToken("refresh-token")).thenReturn(true);
        when(jwtService.extractEmail("refresh-token")).thenReturn("alperen@deneme.com");
        when(jwtService.isTokenValid("refresh-token", "alperen@deneme.com")).thenReturn(true);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        RefreshRequest request = new RefreshRequest("refresh-token");

        assertThatThrownBy(() -> authService.refresh(request)).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void refreshRejectsReusedToken() {
        RefreshToken stored = storedToken("fam-1", true, Instant.now().plusSeconds(60));

        when(jwtService.isRefreshToken("refresh-token")).thenReturn(true);
        when(jwtService.extractEmail("refresh-token")).thenReturn("alperen@deneme.com");
        when(jwtService.isTokenValid("refresh-token", "alperen@deneme.com")).thenReturn(true);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        RefreshRequest request = new RefreshRequest("refresh-token");

        assertThatThrownBy(() -> authService.refresh(request)).isInstanceOf(BadCredentialsException.class);
        verify(refreshTokenRepository).revokeFamily("fam-1");
    }

    @Test
    void refreshReturnsNewTokens() {
        User user = new User();
        user.setId(1L);
        user.setEmail("alperen@deneme.com");
        user.setPasswordHash("hashed");
        user.setRoles(Set.of(Role.CUSTOMER));

        RefreshToken stored = storedToken("fam-1", false, Instant.now().plusSeconds(60));

        when(jwtService.isRefreshToken("refresh-token")).thenReturn(true);
        when(jwtService.extractEmail("refresh-token")).thenReturn("alperen@deneme.com");
        when(jwtService.isTokenValid("refresh-token", "alperen@deneme.com")).thenReturn(true);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));
        when(userRepository.findByEmail("alperen@deneme.com")).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(any())).thenReturn("new-access-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("new-refresh-token");
        when(jwtService.extractExpiration("new-refresh-token")).thenReturn(Instant.now().plusSeconds(600));

        AuthResponse response = authService.refresh(new RefreshRequest("refresh-token"));

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
        assertThat(stored.isRevoked()).isTrue();
        verify(refreshTokenRepository, never()).revokeFamily(anyString());
    }

    @Test
    void logoutRevokesTokenFamily() {
        RefreshToken stored = storedToken("fam-2", false, Instant.now().plusSeconds(60));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        authService.logout(new RefreshRequest("refresh-token"));

        verify(refreshTokenRepository).revokeFamily("fam-2");
    }

    private RefreshToken storedToken(String familyId, boolean revoked, Instant expiresAt) {
        RefreshToken token = new RefreshToken();
        token.setFamilyId(familyId);
        token.setRevoked(revoked);
        token.setExpiresAt(expiresAt);
        return token;
    }

    @Test
    void loginRejectsUnknownUser() {
        when(userRepository.findByEmail("alperen@deneme.com")).thenReturn(Optional.empty());

        LoginRequest request = new LoginRequest("alperen@deneme.com", "password123");

        assertThatThrownBy(() -> authService.login(request)).isInstanceOf(BadCredentialsException.class);
    }
}
