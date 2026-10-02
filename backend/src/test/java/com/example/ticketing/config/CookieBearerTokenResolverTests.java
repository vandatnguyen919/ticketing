package com.example.ticketing.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

class CookieBearerTokenResolverTests {

    private final CookieBearerTokenResolver resolver = new CookieBearerTokenResolver("ticketing-token");

    @Test
    void resolvesOnlyTheConfiguredCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("other", "ignored"), new Cookie("ticketing-token", "signed-jwt"));
        request.addHeader("Authorization", "Bearer header-token");

        assertEquals("signed-jwt", resolver.resolve(request));
    }

    @Test
    void doesNotResolveMissingOrBlankCookie() {
        assertNull(resolver.resolve(new MockHttpServletRequest()));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("ticketing-token", " "));
        assertNull(resolver.resolve(request));
    }

    @Test
    void rejectsDuplicateCredentialCookies() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
            new Cookie("ticketing-token", "first"),
            new Cookie("ticketing-token", "second")
        );

        assertThrows(OAuth2AuthenticationException.class, () -> resolver.resolve(request));
    }
}
