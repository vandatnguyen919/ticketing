package com.example.ticketing.auth.otp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {ResetCacheConfiguration.class, OtpStore.class, ResetTicketStore.class}, properties = {
    "spring.cloud.vault.token=test-vault-token",
    "security.jwt-secret=01234567890123456789012345678901",
    "oauth.github.client-id=test-github-client",
    "oauth.github.client-secret=test-github-secret",
    "oauth.google.client-id=test-google-client",
    "oauth.google.client-secret=test-google-secret",
    "spring.flyway.enabled=false",
    "spring.sql.init.mode=never",
    "app.auth.otp.ttl=250ms",
    "app.auth.otp.max-attempts=3",
    "app.auth.otp.length=6",
    "app.auth.reset-ticket.ttl=250ms",
    "app.auth.reset-request-cooldown=1s",
    "app.auth.mail.from=test@localhost"
})
class OtpStoreTests {

    @Autowired
    private OtpStore otpStore;

    @Autowired
    private ResetTicketStore resetTicketStore;

    @Test
    void holdsTheCodeRecordUntilItIsEvicted() {
        OtpRecord record = new OtpRecord("bcrypt-hash", Instant.now(), 1);

        otpStore.put("holds@example.com", record);

        assertEquals(record, otpStore.find("holds@example.com"));

        otpStore.evict("holds@example.com");

        assertNull(otpStore.find("holds@example.com"));
    }

    @Test
    void dropsTheCodeRecordWhenTheCacheTimeToLivePasses() throws Exception {
        otpStore.put("expires@example.com", new OtpRecord("bcrypt-hash", Instant.now(), 0));
        assertNotNull(otpStore.find("expires@example.com"));

        Thread.sleep(400);

        assertNull(otpStore.find("expires@example.com"));
    }

    @Test
    void keepsResetTicketsInASecondCacheThatAlsoExpires() throws Exception {
        ResetTicketRecord record = new ResetTicketRecord("tickets@example.com", Instant.now());

        resetTicketStore.put("ticket-hash", record);
        assertEquals(record, resetTicketStore.find("ticket-hash"));
        assertNull(otpStore.find("ticket-hash"));

        Thread.sleep(400);

        assertNull(resetTicketStore.find("ticket-hash"));
    }

    @Test
    void returnsNothingForAnUnknownKey() {
        assertNull(otpStore.find("never-stored@example.com"));
        assertNull(resetTicketStore.find("never-stored-ticket"));
    }
}
