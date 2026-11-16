package com.event.event_ticket_app.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.event.event_ticket_app.security.UserPrincipal;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Autowired(required = false)
    private AuditService auditService;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email is already registered");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRoles(Set.of(Role.CUSTOMER));

        System.out.println("Yeni kullanıcı kaydedildi");
        User saved = userRepository.save(user);
        logAudit(saved.getId(), "REGISTER", "User", saved.getId());
        return UserResponse.from(saved);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        System.out.println("Giriş başarılı");
        logAudit(user.getId(), "LOGIN", "User", user.getId());
        return issueTokens(user, UUID.randomUUID().toString());
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String token = request.refreshToken();
        String email = parseRefreshToken(token);

        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(token))
                .orElseThrow(() -> new BadCredentialsException("Refresh token is invalid or expired"));

        if (stored.isRevoked()) {
            // the same refresh token was presented twice: it was already rotated once,
            // so this is either a replay or a stolen token. Kill the whole token family.
            refreshTokenRepository.revokeFamily(stored.getFamilyId());
            throw new BadCredentialsException("Refresh token has already been used");
        }

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new BadCredentialsException("Refresh token is invalid or expired");
        }

        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        return issueTokens(user, stored.getFamilyId());
    }

    @Transactional
    public void logout(RefreshRequest request) {
        String token = request.refreshToken();
        try {
            refreshTokenRepository.findByTokenHash(hash(token))
                    .ifPresent(stored -> refreshTokenRepository.revokeFamily(stored.getFamilyId()));
        } catch (RuntimeException ex) {
            // an unreadable or already-invalid token has nothing left to revoke
        }
    }

    @Scheduled(fixedRate = 3_600_000)
    @Transactional
    public void evictExpiredRefreshTokens() {
        refreshTokenRepository.deleteByExpiresAtBefore(Instant.now());
    }

    private String parseRefreshToken(String token) {
        try {
            if (!jwtService.isRefreshToken(token)) {
                throw new BadCredentialsException("Provided token is not a refresh token");
            }
            String email = jwtService.extractEmail(token);
            if (!jwtService.isTokenValid(token, email)) {
                throw new BadCredentialsException("Refresh token is invalid or expired");
            }
            return email;
        } catch (BadCredentialsException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BadCredentialsException("Refresh token is invalid or expired");
        }
    }

    private AuthResponse issueTokens(User user, String familyId) {
        UserPrincipal principal = new UserPrincipal(user);
        String accessToken = jwtService.generateAccessToken(principal);
        String refreshToken = jwtService.generateRefreshToken(principal);

        RefreshToken record = new RefreshToken();
        record.setTokenHash(hash(refreshToken));
        record.setUserId(user.getId());
        record.setFamilyId(familyId);
        record.setExpiresAt(jwtService.extractExpiration(refreshToken));
        refreshTokenRepository.save(record);

        return new AuthResponse(accessToken, refreshToken);
    }

    private void logAudit(Long actorId, String action, String resourceType, Long resourceId) {
        if (auditService != null) {
            auditService.log(actorId, action, resourceType, resourceId);
        }
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
