package com.example.ticketing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.example.ticketing.model.OAuthProvider;
import com.example.ticketing.model.User;
import com.example.ticketing.model.UserProfile;
import com.example.ticketing.repository.UserRepository;

class UserServiceTests {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserService userService = new UserService(userRepository);

    @Test
    void createsIdentityUsingProviderAndStableSubject() {
        UserProfile profile = new UserProfile("person@example.com", "Person", "google", "google-subject");
        when(userRepository.findByProviderAndProviderId(OAuthProvider.GOOGLE, "google-subject"))
            .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        UserProfile saved = userService.syncUser(profile);

        assertEquals("google", saved.provider());
        assertEquals("google-subject", saved.providerId());
        verify(userRepository).findByProviderAndProviderId(OAuthProvider.GOOGLE, "google-subject");
    }

    @Test
    void updatesOnlyTheMatchingProviderSubject() {
        User existing = new User(4L, "old@example.com", "Old Name", OAuthProvider.GITHUB, "12345");
        UserProfile profile = new UserProfile("new@example.com", "New Name", "github", "12345");
        when(userRepository.findByProviderAndProviderId(OAuthProvider.GITHUB, "12345"))
            .thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        UserProfile saved = userService.syncUser(profile);

        assertEquals("new@example.com", saved.email());
        assertEquals("New Name", saved.name());
        assertEquals("12345", saved.providerId());
        verify(userRepository).findByProviderAndProviderId(OAuthProvider.GITHUB, "12345");
    }

    @Test
    void rejectsProvidersOutsideTheClosedProviderSet() {
        UserProfile profile = new UserProfile("person@example.com", "Person", "unknown", "subject");

        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> userService.syncUser(profile)
        );
        verifyNoInteractions(userRepository);
    }
}
