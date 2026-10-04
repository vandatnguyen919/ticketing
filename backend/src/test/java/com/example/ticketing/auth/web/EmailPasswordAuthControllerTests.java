package com.example.ticketing.auth.web;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.ticketing.auth.dto.LoginRequest;
import com.example.ticketing.auth.dto.RegisterRequest;
import com.example.ticketing.auth.service.EmailAlreadyRegisteredException;
import com.example.ticketing.auth.service.EmailPasswordUserService;
import com.example.ticketing.auth.service.InvalidCredentialsException;
import com.example.ticketing.model.UserProfile;
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
class EmailPasswordAuthControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmailPasswordUserService userService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void registersWithCreatedStatusCookieAndSafeProfile() throws Exception {
        UserProfile profile = new UserProfile("dana@example.com", "Dana", "email", "dana@example.com");
        when(userService.register(any(RegisterRequest.class))).thenReturn(profile);
        when(jwtService.generateToken(any(UserProfile.class)))
            .thenReturn(new JwtService.IssuedToken("signed-jwt", Instant.now().plusSeconds(3600)));

        mockMvc.perform(post("/api/v1/auth/register")
                .cookie(csrfCookie())
                .header("X-XSRF-TOKEN", csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dana@example.com\",\"password\":\"long-enough-password\",\"displayName\":\"Dana\"}"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Set-Cookie", allOf(
                containsString("ticketing-token=signed-jwt"),
                containsString("Path=/api"),
                containsString("Secure"),
                containsString("HttpOnly"),
                containsString("SameSite=Lax")
            )))
            .andExpect(jsonPath("$.user.email").value("dana@example.com"))
            .andExpect(jsonPath("$.user.provider").value("email"))
            .andExpect(jsonPath("$.user.providerId").doesNotExist())
            .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void reportsAConflictWhenTheEmailIsAlreadyRegistered() throws Exception {
        when(userService.register(any(RegisterRequest.class))).thenThrow(new EmailAlreadyRegisteredException());

        mockMvc.perform(post("/api/v1/auth/register")
                .cookie(csrfCookie())
                .header("X-XSRF-TOKEN", csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dana@example.com\",\"password\":\"long-enough-password\",\"displayName\":\"Dana\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("email_taken"));
    }

    @Test
    void rejectsInvalidRegistrationInputWithFieldMessages() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .cookie(csrfCookie())
                .header("X-XSRF-TOKEN", csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\",\"password\":\"short\",\"displayName\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("validation_error"))
            .andExpect(jsonPath("$.message", containsString("email")))
            .andExpect(jsonPath("$.message", containsString("password")))
            .andExpect(jsonPath("$.message", containsString("displayName")));
    }

    @Test
    void logsInWithOkStatusAndCookie() throws Exception {
        UserProfile profile = new UserProfile("dana@example.com", "Dana", "email", "dana@example.com");
        when(userService.login(any(LoginRequest.class))).thenReturn(profile);
        when(jwtService.generateToken(any(UserProfile.class)))
            .thenReturn(new JwtService.IssuedToken("signed-jwt", Instant.now().plusSeconds(3600)));

        mockMvc.perform(post("/api/v1/auth/login")
                .cookie(csrfCookie())
                .header("X-XSRF-TOKEN", csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dana@example.com\",\"password\":\"long-enough-password\"}"))
            .andExpect(status().isOk())
            .andExpect(header().string("Set-Cookie", containsString("ticketing-token=signed-jwt")))
            .andExpect(jsonPath("$.user.email").value("dana@example.com"));
    }

    @Test
    void reportsOneGenericUnauthorizedErrorForLoginFailures() throws Exception {
        when(userService.login(any(LoginRequest.class))).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v1/auth/login")
                .cookie(csrfCookie())
                .header("X-XSRF-TOKEN", csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dana@example.com\",\"password\":\"wrong-password-1\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("invalid_credentials"))
            .andExpect(jsonPath("$.message").value("Email or password is incorrect."));
    }

    @Test
    void requiresTheCsrfTokenForCredentialEndpoints() throws Exception {
        Cookie csrfCookie = csrfCookie();

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dana@example.com\",\"password\":\"long-enough-password\"}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/auth/register")
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", "mismatched-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dana@example.com\",\"password\":\"long-enough-password\",\"displayName\":\"Dana\"}"))
            .andExpect(status().isForbidden());
    }

    private Cookie csrf;

    private Cookie csrfCookie() throws Exception {
        if (csrf != null) {
            return csrf;
        }
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf")).andReturn();
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        if (cookie == null) {
            throw new AssertionError("The CSRF bootstrap endpoint did not issue an XSRF-TOKEN cookie.");
        }
        csrf = cookie;
        return csrf;
    }
}
