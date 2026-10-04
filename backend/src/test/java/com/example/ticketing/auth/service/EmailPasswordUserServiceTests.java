package com.example.ticketing.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.ticketing.auth.dto.LoginRequest;
import com.example.ticketing.auth.dto.RegisterRequest;
import com.example.ticketing.model.OAuthProvider;
import com.example.ticketing.model.User;
import com.example.ticketing.model.UserProfile;
import com.example.ticketing.repository.UserRepository;

class EmailPasswordUserServiceTests {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final EmailPasswordUserService service =
        new EmailPasswordUserService(userRepository, passwordEncoder, authenticationManager);

    @Test
    void registersANormalizedLocalAccountWithABcryptHash() {
        when(userRepository.countByEmailIgnoreCase("dana@example.com")).thenReturn(0L);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserProfile profile = service.register(
            new RegisterRequest("  Dana@Example.com ", "long-enough-password", " Dana ")
        );

        assertEquals("dana@example.com", profile.email());
        assertEquals("Dana", profile.name());
        assertEquals("email", profile.provider());
        User saved = captureSavedUser();
        assertEquals(OAuthProvider.EMAIL, saved.provider());
        assertEquals("dana@example.com", saved.providerId());
        assertEquals(60, saved.passwordHash().length());
        assertEquals(true, passwordEncoder.matches("long-enough-password", saved.passwordHash()));
    }

    @Test
    void refusesToRegisterAnEmailThatAnyAccountAlreadyUses() {
        when(userRepository.countByEmailIgnoreCase("dana@example.com")).thenReturn(1L);

        EmailAlreadyRegisteredException exception = assertThrows(
            EmailAlreadyRegisteredException.class,
            () -> service.register(new RegisterRequest("dana@example.com", "long-enough-password", "Dana"))
        );

        assertEquals("An account already exists for this email address.", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void logsInTheLocalAccountThroughTheAuthenticationManager() {
        when(userRepository.findByProviderAndProviderId(OAuthProvider.EMAIL, "dana@example.com"))
            .thenReturn(Optional.of(localAccount("long-enough-password")));

        UserProfile profile = service.login(new LoginRequest("Dana@Example.com", "long-enough-password"));

        assertEquals("dana@example.com", profile.email());
        assertEquals("Dana", profile.name());
        verify(authenticationManager).authenticate(
            new UsernamePasswordAuthenticationToken("dana@example.com", "long-enough-password")
        );
    }

    @Test
    void reportsOneGenericErrorForEveryLoginFailure() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
            .thenThrow(new BadCredentialsException("wrong"));

        InvalidCredentialsException exception = assertThrows(
            InvalidCredentialsException.class,
            () -> service.login(new LoginRequest("dana@example.com", "wrong-password-1"))
        );

        assertEquals("Email or password is incorrect.", exception.getMessage());
    }

    @Test
    void reportsTheDuplicateEmailErrorWhenTheDatabaseRejectsTheInsert() {
        when(userRepository.countByEmailIgnoreCase("dana@example.com")).thenReturn(0L);
        when(userRepository.save(any(User.class)))
            .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key"));

        EmailAlreadyRegisteredException exception = assertThrows(
            EmailAlreadyRegisteredException.class,
            () -> service.register(new RegisterRequest("dana@example.com", "long-enough-password", "Dana"))
        );

        assertEquals("An account already exists for this email address.", exception.getMessage());
    }

    private User captureSavedUser() {
        var argument = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(argument.capture());
        return argument.getValue();
    }

    private User localAccount(String passwordHash) {
        return new User(5L, "dana@example.com", "Dana", OAuthProvider.EMAIL, "dana@example.com", passwordHash);
    }
}
