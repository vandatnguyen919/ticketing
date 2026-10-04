package com.example.ticketing.auth.mail;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import com.example.ticketing.auth.AuthProperties;

@SpringBootTest(classes = {MailConfiguration.class, JavaMailAuthEmailService.class})
@EnableConfigurationProperties(AuthProperties.class)
@Import(AsyncMailDispatchTests.MailSenderStub.class)
class AsyncMailDispatchTests {

    static final CountDownLatch SEND_STARTED = new CountDownLatch(1);
    static final CountDownLatch SEND_RELEASED = new CountDownLatch(1);

    @Autowired
    private AuthEmailService emailService;

    @Autowired
    private JavaMailSender mailSender;

    @Test
    void handsTheEmailOffWithoutWaitingForTheSend() throws Exception {
        AtomicLong sendDurationMs = new AtomicLong();
        doAnswer(invocation -> {
            SEND_STARTED.countDown();
            long start = System.nanoTime();
            SEND_RELEASED.await(5, TimeUnit.SECONDS);
            sendDurationMs.set(Duration.ofNanos(System.nanoTime() - start).toMillis());
            return null;
        }).when(mailSender).send(any(MimeMessage.class));

        long callStart = System.nanoTime();
        emailService.sendPasswordResetCode("dana@example.com", "Dana", "123456");
        long callDurationMs = Duration.ofNanos(System.nanoTime() - callStart).toMillis();

        assertTrue(SEND_STARTED.await(2, TimeUnit.SECONDS), "The send should run on another thread");
        assertTrue(callDurationMs < 1000, "The caller waited " + callDurationMs + "ms for the send");
        assertTrue(sendDurationMs.get() == 0, "The send should still be blocked when the caller returns");
        SEND_RELEASED.countDown();
    }

    @Test
    void swallowsSendFailuresInsteadOfSurfacingThemToTheCaller() {
        doThrow(new MailSendException("SMTP is down")).when(mailSender).send(any(MimeMessage.class));

        assertDoesNotThrow(() -> emailService.sendPasswordResetCode("dana@example.com", "Dana", "123456"));
    }

    @TestConfiguration
    static class MailSenderStub {

        @Bean
        JavaMailSender mailSender() {
            JavaMailSender sender = mock(JavaMailSender.class);
            when(sender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
            return sender;
        }
    }
}
