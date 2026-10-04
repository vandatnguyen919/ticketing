package com.example.ticketing.auth.otp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;

import com.github.benmanes.caffeine.cache.Cache;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;

import com.example.ticketing.auth.AuthProperties;

@SpringBootTest(classes = ResetCacheConfiguration.class, properties = {
    "spring.cloud.vault.token=test-vault-token",
    "security.jwt-secret=01234567890123456789012345678901",
    "oauth.github.client-id=test-github-client",
    "oauth.github.client-secret=test-github-secret",
    "oauth.google.client-id=test-google-client",
    "oauth.google.client-secret=test-google-secret",
    "spring.flyway.enabled=false",
    "spring.sql.init.mode=never"
})
class ResetCacheConfigurationTests {

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private AuthProperties properties;

    @Test
    void keepsResetTimeToLivesInCacheConfigurationAndAppliesThem() {
        assertEquals(Duration.ofMinutes(10), properties.otp().ttl());
        assertEquals(Duration.ofMinutes(5), properties.resetTicket().ttl());
        assertEquals(Duration.ofMinutes(10), configuredTtlOf(OtpStore.CACHE_NAME));
        assertEquals(Duration.ofMinutes(5), configuredTtlOf(ResetTicketStore.CACHE_NAME));
    }

    @SuppressWarnings("unchecked")
    private Duration configuredTtlOf(String cacheName) {
        org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
        assertNotNull(cache, "The cache manager must provide " + cacheName);
        Cache<Object, Object> caffeine = (Cache<Object, Object>) cache.getNativeCache();
        return caffeine.policy().expireAfterWrite().orElseThrow().getExpiresAfter();
    }
}
