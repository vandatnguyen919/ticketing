package com.example.ticketing;

import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import jakarta.servlet.http.Cookie;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.ticketing.config.OAuth2LoginSuccessHandler;
import com.example.ticketing.model.BookingResponse;
import com.example.ticketing.model.UserProfile;
import com.example.ticketing.service.EventService;
import com.example.ticketing.service.JwtService;

@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "spring.sql.init.mode=never",
    "spring.cloud.vault.token=test-vault-token",
    "oauth.github.client-id=test-github-client",
    "oauth.github.client-secret=test-github-secret",
    "oauth.google.client-id=test-google-client",
    "oauth.google.client-secret=test-google-secret",
    "security.jwt-secret=01234567890123456789012345678901",
    "app.security.cookie.secure=true"
})
@AutoConfigureMockMvc
class ApiVersionAndCookieAuthTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventService eventService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void mapsOnlyTheSupportedVersionedRestPaths() throws Exception {
        when(eventService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/auth/providers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.github").value("/oauth2/authorization/github"));

        mockMvc.perform(get("/api/v1/events"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(get("/api/v2/auth/providers"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/auth/providers"))
            .andExpect(status().isNotFound());
    }

    @Test
    void requiresCookieAuthenticationForCurrentUser() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void initializesReadableRootScopedCsrfCookieWithoutReturningToken() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""))
            .andExpect(header().string("Set-Cookie", allOf(
                containsString("XSRF-TOKEN="),
                containsString("Path=/"),
                containsString("Secure")
            )))
            .andReturn();
        Cookie csrfCookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(csrfCookie);
        assertEquals("/", csrfCookie.getPath());
        assertTrue(csrfCookie.getSecure());
        assertFalse(csrfCookie.isHttpOnly());
        assertEquals("Lax", csrfCookie.getAttribute("SameSite"));
    }

    @Test
    void rejectsInvalidAndExpiredCredentialCookies() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").cookie(new Cookie("ticketing-token", "not-a-jwt")))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/auth/me").cookie(new Cookie("ticketing-token", expiredToken())))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void unsafeLogoutRequiresCsrfAndExpiresTheCookie() throws Exception {
        Cookie csrfCookie = csrfCookie();

        mockMvc.perform(post("/api/v1/auth/logout").cookie(csrfCookie))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/auth/logout")
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", "mismatched-token"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/auth/logout")
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue()))
            .andExpect(status().isNoContent())
            .andExpect(header().string("Set-Cookie", allOf(
                containsString("ticketing-token="),
                containsString("Path=/api"),
                containsString("Max-Age=0"),
                containsString("HttpOnly"),
                containsString("SameSite=Lax")
            )));
    }

    @Test
    void bookingRequiresTheCsrfCookieAndMatchingHeader() throws Exception {
        Cookie authenticationCookie = new Cookie("ticketing-token", validToken());
        Cookie csrfCookie = csrfCookie();
        when(eventService.bookTicket(1L, "person@example.com"))
            .thenReturn(new BookingResponse(1L, "person@example.com", 4));

        mockMvc.perform(post("/api/v1/events/1/book")
                .cookie(authenticationCookie))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/events/1/book")
                .cookie(authenticationCookie, csrfCookie))
            .andExpect(status().isForbidden());

        MvcResult successfulBooking = mockMvc.perform(post("/api/v1/events/1/book")
                .cookie(authenticationCookie, csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue()))
            .andExpect(status().isOk())
            .andReturn();
        assertTrue(
            successfulBooking.getResponse().getHeaders("Set-Cookie").isEmpty(),
            successfulBooking.getResponse().getHeaders("Set-Cookie").toString()
        );
    }

    @Test
    void exchangeSetsSecureHttpOnlySameSiteCookieAndDoesNotReturnJwt() throws Exception {
        Cookie csrfCookie = csrfCookie();
        UserProfile profile = new UserProfile("person@example.com", "Example Person", "google", "google-subject");
        when(jwtService.generateToken(profile))
            .thenReturn(new JwtService.IssuedToken("signed-jwt", Instant.now().plusSeconds(3600)));

        mockMvc.perform(post("/api/v1/auth/exchange-session")
                .with(SecurityMockMvcRequestPostProcessors.oauth2Login())
            .cookie(csrfCookie)
            .header("X-XSRF-TOKEN", csrfCookie.getValue())
                .sessionAttr(OAuth2LoginSuccessHandler.USER_PROFILE_SESSION_ATTRIBUTE, profile)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(header().string("Set-Cookie",
                allOf(
                    containsString("ticketing-token=signed-jwt"),
                    containsString("Path=/api"),
                    containsString("Secure"),
                    containsString("HttpOnly"),
                    containsString("SameSite=Lax")
                )))
            .andExpect(jsonPath("$.user.email").value("person@example.com"))
            .andExpect(jsonPath("$.token").doesNotExist())
            .andExpect(jsonPath("$.user.providerId").doesNotExist());
    }

    @Test
    void letsBrowsersCacheCrossOriginPreflightChecks() throws Exception {
        mockMvc.perform(options("/api/v1/events/12/book")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "X-XSRF-TOKEN"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Headers", containsString("X-XSRF-TOKEN")))
            .andExpect(header().string("Access-Control-Allow-Methods", containsString("PATCH")))
            .andExpect(header().string("Access-Control-Max-Age", "3600"));
    }

    private String expiredToken() {
        Instant expiration = Instant.now().minusSeconds(300);
        return Jwts.builder()
            .subject("person@example.com")
            .issuedAt(Date.from(expiration.minusSeconds(600)))
            .expiration(Date.from(expiration))
            .signWith(
            Keys.hmacShaKeyFor("01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)),
            Jwts.SIG.HS256
            )
            .compact();
    }

    private String validToken() {
        Instant issuedAt = Instant.now();
        return Jwts.builder()
            .subject("person@example.com")
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(issuedAt.plusSeconds(3600)))
            .claim("name", "Example Person")
            .claim("provider", "google")
            .claim("providerId", "google-subject")
            .signWith(
                Keys.hmacShaKeyFor("01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)),
                Jwts.SIG.HS256
            )
            .compact();
    }

    private Cookie csrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
            .andExpect(status().isNoContent())
            .andReturn();
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        if (cookie == null) {
            throw new AssertionError("The CSRF bootstrap endpoint did not issue an XSRF-TOKEN cookie.");
        }
        return cookie;
    }
}
