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

import com.example.ticketing.config.OAuth2LoginSuccessHandler;
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
    void rejectsInvalidAndExpiredCredentialCookies() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").cookie(new Cookie("ticketing-token", "not-a-jwt")))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/auth/me").cookie(new Cookie("ticketing-token", expiredToken())))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void unsafeLogoutRequiresCsrfAndExpiresTheCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/auth/logout")
                .with(SecurityMockMvcRequestPostProcessors.csrf()))
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
    void exchangeSetsSecureHttpOnlySameSiteCookieAndDoesNotReturnJwt() throws Exception {
        UserProfile profile = new UserProfile("person@example.com", "Example Person", "google", "google-subject");
        when(jwtService.generateToken(profile))
            .thenReturn(new JwtService.IssuedToken("signed-jwt", Instant.now().plusSeconds(3600)));

        mockMvc.perform(post("/api/v1/auth/exchange-session")
                .with(SecurityMockMvcRequestPostProcessors.oauth2Login())
                .with(SecurityMockMvcRequestPostProcessors.csrf())
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
}
