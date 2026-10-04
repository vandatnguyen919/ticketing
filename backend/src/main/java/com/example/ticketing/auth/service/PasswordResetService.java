package com.example.ticketing.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing.auth.AuthProperties;
import com.example.ticketing.auth.EmailNormalizer;
import com.example.ticketing.auth.dto.PasswordResetVerifyResponse;
import com.example.ticketing.auth.mail.AuthEmailService;
import com.example.ticketing.auth.otp.OtpRecord;
import com.example.ticketing.auth.otp.OtpStore;
import com.example.ticketing.auth.otp.ResetTicketRecord;
import com.example.ticketing.auth.otp.ResetTicketStore;
import com.example.ticketing.model.OAuthProvider;
import com.example.ticketing.model.User;
import com.example.ticketing.repository.UserRepository;

@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final OtpStore otpStore;
    private final ResetTicketStore resetTicketStore;
    private final PasswordEncoder passwordEncoder;
    private final AuthEmailService emailService;
    private final AuthProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(
        UserRepository userRepository,
        OtpStore otpStore,
        ResetTicketStore resetTicketStore,
        PasswordEncoder passwordEncoder,
        AuthEmailService emailService,
        AuthProperties properties
    ) {
        this.userRepository = userRepository;
        this.otpStore = otpStore;
        this.resetTicketStore = resetTicketStore;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.properties = properties;
    }

    public void requestReset(String rawEmail) {
        String email = EmailNormalizer.normalize(rawEmail);
        Instant now = Instant.now();
        OtpRecord existing = otpStore.find(email);
        if (existing != null && existing.createdAt().isAfter(now.minus(properties.resetRequestCooldown()))) {
            return;
        }
        User account = userRepository.findByProviderAndProviderId(OAuthProvider.EMAIL, email)
            .filter(user -> user.passwordHash() != null)
            .orElse(null);
        if (account == null) {
            return;
        }
        String code = generateCode();
        otpStore.put(email, new OtpRecord(passwordEncoder.encode(code), now, 0));
        emailService.sendPasswordResetCode(account.email(), account.displayName(), code);
    }

    public PasswordResetVerifyResponse verify(String rawEmail, String rawCode) {
        String email = EmailNormalizer.normalize(rawEmail);
        OtpRecord record = otpStore.find(email);
        if (record == null) {
            throw new PasswordResetException(
                PasswordResetException.OTP_EXPIRED,
                "The code is invalid or has expired. Request a new one."
            );
        }
        if (record.attempts() >= properties.otp().maxAttempts()) {
            throw locked();
        }
        if (!passwordEncoder.matches(rawCode, record.codeHash())) {
            int attempts = record.attempts() + 1;
            otpStore.put(email, new OtpRecord(record.codeHash(), record.createdAt(), attempts));
            if (attempts >= properties.otp().maxAttempts()) {
                throw locked();
            }
            throw new PasswordResetException(PasswordResetException.INVALID_OTP, "The code is incorrect.");
        }
        String ticket = generateTicket();
        resetTicketStore.put(hash(ticket), new ResetTicketRecord(email, Instant.now()));
        return new PasswordResetVerifyResponse(ticket, properties.resetTicket().ttl().toSeconds());
    }

    @Transactional
    public void confirm(String rawTicket, String newPassword) {
        String ticketHash = hash(rawTicket);
        ResetTicketRecord record = resetTicketStore.find(ticketHash);
        if (record == null) {
            throw invalidTicket();
        }
        OtpRecord code = otpStore.find(record.accountEmail());
        if (code == null || record.createdAt().isBefore(code.createdAt())) {
            throw invalidTicket();
        }
        User account = userRepository.findByProviderAndProviderId(OAuthProvider.EMAIL, record.accountEmail())
            .orElseThrow(this::invalidTicket);
        userRepository.save(new User(
            account.id(),
            account.email(),
            account.displayName(),
            account.provider(),
            account.providerId(),
            passwordEncoder.encode(newPassword)
        ));
        resetTicketStore.evict(ticketHash);
        otpStore.evict(record.accountEmail());
    }

    private PasswordResetException locked() {
        return new PasswordResetException(
            PasswordResetException.OTP_LOCKED,
            "Too many failed attempts. Request a new code."
        );
    }

    private PasswordResetException invalidTicket() {
        return new PasswordResetException(
            PasswordResetException.RESET_TICKET_INVALID,
            "The reset request is invalid or has expired."
        );
    }

    private String generateCode() {
        int length = properties.otp().length();
        int bound = (int) Math.pow(10, length);
        return String.format("%0" + length + "d", secureRandom.nextInt(bound));
    }

    private String generateTicket() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }
}
