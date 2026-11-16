package com.event.event_ticket_app.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.event.event_ticket_app.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
