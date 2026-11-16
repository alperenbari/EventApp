package com.event.event_ticket_app.config;

import java.util.Set;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.event.event_ticket_app.entity.Role;
import com.event.event_ticket_app.entity.User;
import com.event.event_ticket_app.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        System.out.println("Kullanıcılar oluşturuluyor");
        seedUser("admin@eventticket.com", "Admin123!", Role.ADMIN);
        seedUser("organizer@eventticket.com", "Organizer123!", Role.ORGANIZER);
        seedUser("customer@eventticket.com", "Customer123!", Role.CUSTOMER);
    }

    private void seedUser(String email, String rawPassword, Role role) {
        if (userRepository.existsByEmail(email)) {
            return;
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRoles(Set.of(role));

        userRepository.save(user);
    }
}
