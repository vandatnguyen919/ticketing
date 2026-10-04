package com.example.ticketing.auth.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.ticketing.auth.dto.PasswordResetVerifyResponse;
import com.example.ticketing.auth.service.PasswordResetException;
import com.example.ticketing.auth.service.PasswordResetService;

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
class PasswordResetControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PasswordResetService passwordResetService;

    @Test
    void acceptsEveryResetRequestWithTheSameAcceptedBody() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                .cookie(csrfCookie())
                .header("X-XSRF-TOKEN", csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dana@example.com\"}"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("RESET_REQUEST_ACCEPTED"))
            .andExpect(jsonPath("$.message", containsString("If an account exists for this email")));

        verify(passwordResetService).requestReset("dana@example.com");
    }

    @Test
    void rejectsAMalformedResetRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                .cookie(csrfCookie())
                .header("X-XSRF-TOKEN", csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("validation_error"));

        verifyNoInteractions(passwordResetService);
    }

    @Test
    void returnsAShortLivedTicketWhenTheCodeMatches() throws Exception {
        when(passwordResetService.verify(anyString(), anyString()))
            .thenReturn(new PasswordResetVerifyResponse("opaque-reset-ticket", 300));

        mockMvc.perform(post("/api/v1/auth/password-reset/verify")
                .cookie(csrfCookie())
                .header("X-XSRF-TOKEN", csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dana@example.com\",\"code\":\"481920\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resetTicket").value("opaque-reset-ticket"))
            .andExpect(jsonPath("$.expiresInSeconds").value(300));
    }

    @Test
    void reportsEachOtpFailureWithItsOwnErrorCode() throws Exception {
        doThrow(new PasswordResetException(PasswordResetException.INVALID_OTP, "The code is incorrect."))
            .when(passwordResetService).verify(anyString(), anyString());

        mockMvc.perform(post("/api/v1/auth/password-reset/verify")
                .cookie(csrfCookie())
                .header("X-XSRF-TOKEN", csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dana@example.com\",\"code\":\"000000\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("invalid_otp"));

        doThrow(new PasswordResetException(PasswordResetException.OTP_EXPIRED, "Expired."))
            .when(passwordResetService).verify(anyString(), anyString());

        mockMvc.perform(post("/api/v1/auth/password-reset/verify")
                .cookie(csrfCookie())
                .header("X-XSRF-TOKEN", csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dana@example.com\",\"code\":\"000000\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("otp_expired"));

        doThrow(new PasswordResetException(PasswordResetException.OTP_LOCKED, "Locked."))
            .when(passwordResetService).verify(anyString(), anyString());

        mockMvc.perform(post("/api/v1/auth/password-reset/verify")
                .cookie(csrfCookie())
                .header("X-XSRF-TOKEN", csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dana@example.com\",\"code\":\"000000\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("otp_locked"));
    }

    @Test
    void confirmsWithNoContentAndConsumesTheTicket() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                .cookie(csrfCookie())
                .header("X-XSRF-TOKEN", csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resetTicket\":\"opaque-reset-ticket\",\"newPassword\":\"a-brand-new-password\"}"))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));

        verify(passwordResetService).confirm("opaque-reset-ticket", "a-brand-new-password");
    }

    @Test
    void rejectsAnUnknownOrReusedTicket() throws Exception {
        doThrow(new PasswordResetException(
            PasswordResetException.RESET_TICKET_INVALID, "The reset request is invalid or has expired."
        )).when(passwordResetService).confirm(anyString(), anyString());

        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                .cookie(csrfCookie())
                .header("X-XSRF-TOKEN", csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resetTicket\":\"opaque-reset-ticket\",\"newPassword\":\"a-brand-new-password\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("reset_ticket_invalid"));
    }

    @Test
    void rejectsANewPasswordThatFailsThePasswordRules() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                .cookie(csrfCookie())
                .header("X-XSRF-TOKEN", csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resetTicket\":\"opaque-reset-ticket\",\"newPassword\":\"short\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("validation_error"))
            .andExpect(jsonPath("$.message", containsString("newPassword")));
    }

    @Test
    void exposesTheResetRoutesOnlyUnderTheSupportedVersion() throws Exception {
        mockMvc.perform(post("/api/v2/auth/password-reset/request")
                .cookie(csrfCookie())
                .header("X-XSRF-TOKEN", csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dana@example.com\"}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/auth/password-reset/request")
                .cookie(csrfCookie())
                .header("X-XSRF-TOKEN", csrfCookie().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dana@example.com\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void requiresTheCsrfTokenForEveryResetStep() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dana@example.com\"}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/auth/password-reset/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dana@example.com\",\"code\":\"481920\"}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resetTicket\":\"opaque-reset-ticket\",\"newPassword\":\"a-brand-new-password\"}"))
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
