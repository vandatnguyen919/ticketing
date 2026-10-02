package com.example.ticketing.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.web.server.ResponseStatusException;

import com.example.ticketing.service.JwtService;

class AuthControllerTests {

    private final AuthController controller = new AuthController(
        mock(JwtService.class),
        "ticketing-token",
        "/api",
        true,
        "Lax"
    );

    @Test
    void rejectsExchangeWithoutOAuthAuthentication() {
        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> controller.exchangeOAuthSession(
                null,
                new MockHttpServletRequest(),
                new MockHttpServletResponse()
            )
        );

        assertEquals(401, exception.getStatusCode().value());
    }

    @Test
    void rejectsAnExpiredOAuthSessionWithoutAStoredVerifiedProfile() {
        OAuth2AuthenticationToken authentication = googleAuthentication();
        HttpServletResponse response = new MockHttpServletResponse();

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> controller.exchangeOAuthSession(
                authentication,
                new MockHttpServletRequest(),
                response
            )
        );

        assertEquals(401, exception.getStatusCode().value());
    }

    private OAuth2AuthenticationToken googleAuthentication() {
        var authorities = Set.of(new SimpleGrantedAuthority("ROLE_USER"));
        var principal = new DefaultOAuth2User(authorities, Map.of("sub", "google-subject"), "sub");
        return new OAuth2AuthenticationToken(principal, authorities, "google");
    }
}
