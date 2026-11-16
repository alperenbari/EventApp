package com.event.event_ticket_app.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.event.event_ticket_app.entity.Role;
import com.event.event_ticket_app.entity.User;

class UserPrincipalTest {

    @Test
    void mapsRolesToAuthorities() {
        User user = new User();
        user.setEmail("alperen@deneme.com");
        user.setPasswordHash("hashed");
        user.setRoles(Set.of(Role.ADMIN));

        UserPrincipal principal = new UserPrincipal(user);

        assertThat(principal.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void hasRoleChecksGrantedRole() {
        User user = new User();
        user.setEmail("alperen@deneme.com");
        user.setPasswordHash("hashed");
        user.setRoles(Set.of(Role.ORGANIZER));

        UserPrincipal principal = new UserPrincipal(user);

        assertThat(principal.hasRole("ORGANIZER")).isTrue();
        assertThat(principal.hasRole("ADMIN")).isFalse();
    }

    @Test
    void usernameIsEmail() {
        User user = new User();
        user.setEmail("alperen@deneme.com");
        user.setPasswordHash("hashed");
        user.setRoles(Set.of(Role.CUSTOMER));

        UserPrincipal principal = new UserPrincipal(user);

        assertThat(principal.getUsername()).isEqualTo("alperen@deneme.com");
        assertThat(principal.getPassword()).isEqualTo("hashed");
    }
}
