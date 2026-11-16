package com.event.event_ticket_app.dto.auth;

import java.util.Set;

import com.event.event_ticket_app.entity.Role;
import com.event.event_ticket_app.entity.User;

public record UserResponse(Long id, String email, Set<Role> roles) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRoles());
    }
}
