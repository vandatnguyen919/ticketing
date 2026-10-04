package com.example.ticketing.auth.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.example.ticketing.model.OAuthProvider;
import com.example.ticketing.model.User;
import com.example.ticketing.repository.UserRepository;

class LocalUserDetailsServiceTests {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final LocalUserDetailsService userDetailsService = new LocalUserDetailsService(userRepository);

    @Test
    void loadsTheStoredBcryptHashForTheNormalizedEmail() {
        when(userRepository.findByProviderAndProviderId(OAuthProvider.EMAIL, "dana@example.com"))
            .thenReturn(Optional.of(
                new User(1L, "dana@example.com", "Dana", OAuthProvider.EMAIL, "dana@example.com", "bcrypt-hash")
            ));

        var user = userDetailsService.loadUserByUsername(" Dana@Example.com ");

        assertEquals("dana@example.com", user.getUsername());
        assertEquals("bcrypt-hash", user.getPassword());
    }

    @Test
    void refusesAccountsThatHaveNoLocalPassword() {
        when(userRepository.findByProviderAndProviderId(OAuthProvider.EMAIL, "dana@example.com"))
            .thenReturn(Optional.of(
                new User(1L, "dana@example.com", "Dana", OAuthProvider.GITHUB, "github-subject", null)
            ));

        assertThrows(
            UsernameNotFoundException.class,
            () -> userDetailsService.loadUserByUsername("dana@example.com")
        );
    }

    @Test
    void refusesUnknownEmails() {
        when(userRepository.findByProviderAndProviderId(OAuthProvider.EMAIL, "missing@example.com"))
            .thenReturn(Optional.empty());

        assertThrows(
            UsernameNotFoundException.class,
            () -> userDetailsService.loadUserByUsername("missing@example.com")
        );
    }
}
