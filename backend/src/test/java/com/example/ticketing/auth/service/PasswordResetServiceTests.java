package com.example.ticketing.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.ticketing.auth.AuthProperties;
import com.example.ticketing.auth.dto.PasswordResetVerifyResponse;
import com.example.ticketing.auth.mail.AuthEmailService;
import com.example.ticketing.auth.otp.OtpRecord;
import com.example.ticketing.auth.otp.OtpStore;
import com.example.ticketing.auth.otp.ResetTicketRecord;
import com.example.ticketing.auth.otp.ResetTicketStore;
import com.example.ticketing.model.OAuthProvider;
import com.example.ticketing.model.User;
import com.example.ticketing.repository.UserRepository;

class PasswordResetServiceTests {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final OtpStore otpStore = mock(OtpStore.class);
    private final ResetTicketStore resetTicketStore = mock(ResetTicketStore.class);
    private final AuthEmailService emailService = mock(AuthEmailService.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final AuthProperties properties = new AuthProperties(
        new AuthProperties.Otp(Duration.ofMinutes(10), 5, 6),
        new AuthProperties.ResetTicket(Duration.ofMinutes(5)),
        Duration.ofSeconds(60),
        new AuthProperties.Mail("ticketing@localhost")
    );
    private final PasswordResetService service = new PasswordResetService(
        userRepository, otpStore, resetTicketStore, passwordEncoder, emailService, properties
    );

    @Test
    void generatesAHashedCodeAndDispatchesItForALocalAccount() {
        when(userRepository.findByProviderAndProviderId(OAuthProvider.EMAIL, "dana@example.com"))
            .thenReturn(Optional.of(localAccount()));

        service.requestReset("Dana@Example.com");

        ArgumentCaptor<OtpRecord> recordCaptor = ArgumentCaptor.forClass(OtpRecord.class);
        verify(otpStore).put(eq("dana@example.com"), recordCaptor.capture());
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetCode(eq("dana@example.com"), eq("Dana"), codeCaptor.capture());

        String code = codeCaptor.getValue();
        assertTrue(code.matches("\\d{6}"), "The code should be six digits, was: " + code);
        assertEquals(0, recordCaptor.getValue().attempts());
        assertTrue(passwordEncoder.matches(code, recordCaptor.getValue().codeHash()));
    }

    @Test
    void ignoresRequestsInsideTheCooldown() {
        when(otpStore.find("dana@example.com")).thenReturn(new OtpRecord("hash", Instant.now(), 0));

        service.requestReset("dana@example.com");

        verify(otpStore, never()).put(anyString(), any(OtpRecord.class));
        verifyNoInteractions(emailService);
    }

    @Test
    void replacesTheCodeWhenTheCooldownHasPassed() {
        when(otpStore.find("dana@example.com"))
            .thenReturn(new OtpRecord("hash", Instant.now().minus(Duration.ofMinutes(2)), 3));
        when(userRepository.findByProviderAndProviderId(OAuthProvider.EMAIL, "dana@example.com"))
            .thenReturn(Optional.of(localAccount()));

        service.requestReset("dana@example.com");

        ArgumentCaptor<OtpRecord> recordCaptor = ArgumentCaptor.forClass(OtpRecord.class);
        verify(otpStore).put(eq("dana@example.com"), recordCaptor.capture());
        assertEquals(0, recordCaptor.getValue().attempts(), "A fresh code starts with a clean attempt count");
        verify(emailService).sendPasswordResetCode(eq("dana@example.com"), eq("Dana"), anyString());
    }

    @Test
    void storesAndSendsNothingForAnUnknownAccount() {
        when(userRepository.findByProviderAndProviderId(OAuthProvider.EMAIL, "nobody@example.com"))
            .thenReturn(Optional.empty());

        service.requestReset("nobody@example.com");

        verify(otpStore, never()).put(anyString(), any(OtpRecord.class));
        verifyNoInteractions(emailService);
    }

    @Test
    void mintsASingleUseTicketForTheCorrectCode() {
        String code = "123456";
        when(otpStore.find("dana@example.com"))
            .thenReturn(new OtpRecord(passwordEncoder.encode(code), Instant.now(), 0));

        PasswordResetVerifyResponse response = service.verify("dana@example.com", code);

        assertTrue(response.resetTicket().length() >= 32);
        assertEquals(300, response.expiresInSeconds());

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ResetTicketRecord> recordCaptor = ArgumentCaptor.forClass(ResetTicketRecord.class);
        verify(resetTicketStore).put(keyCaptor.capture(), recordCaptor.capture());
        assertNotEquals(response.resetTicket(), keyCaptor.getValue(), "The cache key must not be the usable ticket");
        assertEquals("dana@example.com", recordCaptor.getValue().accountEmail());
    }

    @Test
    void rejectsAWrongCodeAndCountsTheAttempt() {
        when(otpStore.find("dana@example.com"))
            .thenReturn(new OtpRecord(passwordEncoder.encode("123456"), Instant.now(), 0));

        PasswordResetException exception = assertThrows(
            PasswordResetException.class,
            () -> service.verify("dana@example.com", "000000")
        );

        assertEquals(PasswordResetException.INVALID_OTP, exception.error());
        verify(otpStore).put(eq("dana@example.com"), org.mockito.ArgumentMatchers.argThat(
            record -> record.attempts() == 1
        ));
    }

    @Test
    void locksTheCodeAfterTheConfiguredAttempts() {
        when(otpStore.find("dana@example.com"))
            .thenReturn(new OtpRecord(passwordEncoder.encode("123456"), Instant.now(), 4));

        PasswordResetException exception = assertThrows(
            PasswordResetException.class,
            () -> service.verify("dana@example.com", "000000")
        );

        assertEquals(PasswordResetException.OTP_LOCKED, exception.error());
    }

    @Test
    void refusesALockedCodeEvenWhenItMatches() {
        when(otpStore.find("dana@example.com"))
            .thenReturn(new OtpRecord(passwordEncoder.encode("123456"), Instant.now(), 5));

        PasswordResetException exception = assertThrows(
            PasswordResetException.class,
            () -> service.verify("dana@example.com", "123456")
        );

        assertEquals(PasswordResetException.OTP_LOCKED, exception.error());
    }

    @Test
    void reportsAnExpiredCodeWhenTheCacheHoldsNothing() {
        PasswordResetException exception = assertThrows(
            PasswordResetException.class,
            () -> service.verify("dana@example.com", "123456")
        );

        assertEquals(PasswordResetException.OTP_EXPIRED, exception.error());
    }

    @Test
    void storesTheNewPasswordAndClearsTheResetState() {
        String ticket = "opaque-reset-ticket";
        Instant codeCreatedAt = Instant.now().minusSeconds(30);
        when(resetTicketStore.find(anyString()))
            .thenReturn(new ResetTicketRecord("dana@example.com", Instant.now()));
        when(otpStore.find("dana@example.com"))
            .thenReturn(new OtpRecord("hash", codeCreatedAt, 0));
        when(userRepository.findByProviderAndProviderId(OAuthProvider.EMAIL, "dana@example.com"))
            .thenReturn(Optional.of(localAccount()));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.confirm(ticket, "a-brand-new-password");

        ArgumentCaptor<User> savedCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedCaptor.capture());
        User saved = savedCaptor.getValue();
        assertEquals(5L, saved.id());
        assertEquals("dana@example.com", saved.providerId());
        assertTrue(passwordEncoder.matches("a-brand-new-password", saved.passwordHash()));

        ArgumentCaptor<String> evictedTicket = ArgumentCaptor.forClass(String.class);
        verify(resetTicketStore).evict(evictedTicket.capture());
        assertNotEquals(ticket, evictedTicket.getValue(), "The cache key must not be the usable ticket");
        verify(otpStore).evict("dana@example.com");
    }

    @Test
    void rejectsEveryTicketOnceTheResetFlowIsClosed() {
        when(resetTicketStore.find(anyString()))
            .thenReturn(new ResetTicketRecord("dana@example.com", Instant.now()));
        when(otpStore.find("dana@example.com")).thenReturn(null);

        PasswordResetException exception = assertThrows(
            PasswordResetException.class,
            () -> service.confirm("sibling-ticket", "a-brand-new-password")
        );

        assertEquals(PasswordResetException.RESET_TICKET_INVALID, exception.error());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void rejectsATicketMintedBeforeTheCodeWasReplaced() {
        Instant codeCreatedAt = Instant.now();
        when(resetTicketStore.find(anyString()))
            .thenReturn(new ResetTicketRecord("dana@example.com", codeCreatedAt.minusSeconds(30)));
        when(otpStore.find("dana@example.com"))
            .thenReturn(new OtpRecord("hash", codeCreatedAt, 0));

        PasswordResetException exception = assertThrows(
            PasswordResetException.class,
            () -> service.confirm("stale-ticket", "a-brand-new-password")
        );

        assertEquals(PasswordResetException.RESET_TICKET_INVALID, exception.error());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void refusesAnUnknownOrAlreadyUsedTicket() {
        when(resetTicketStore.find(anyString())).thenReturn(null);

        PasswordResetException exception = assertThrows(
            PasswordResetException.class,
            () -> service.confirm("opaque-reset-ticket", "a-brand-new-password")
        );

        assertEquals(PasswordResetException.RESET_TICKET_INVALID, exception.error());
        verify(userRepository, never()).save(any(User.class));
    }

    private User localAccount() {
        return new User(
            5L,
            "dana@example.com",
            "Dana",
            OAuthProvider.EMAIL,
            "dana@example.com",
            passwordEncoder.encode("the-old-password")
        );
    }
}
