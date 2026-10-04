package com.example.ticketing.auth.mail;

import java.nio.charset.StandardCharsets;

import jakarta.mail.internet.MimeMessage;

import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.example.ticketing.auth.AuthProperties;

@Service
public class JavaMailAuthEmailService implements AuthEmailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public JavaMailAuthEmailService(JavaMailSender mailSender, AuthProperties properties) {
        this.mailSender = mailSender;
        this.fromAddress = properties.mail().from();
    }

    @Async
    @Override
    public void sendPasswordResetCode(String toEmail, String displayName, String code) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject("Your password reset code");
            helper.setText(
                "Hello " + displayName + ",\n\n"
                    + "Your password reset code is: " + code + "\n\n"
                    + "If you did not request this, you can ignore this email.",
                false
            );
            mailSender.send(message);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not send the password reset email.", exception);
        }
    }
}
