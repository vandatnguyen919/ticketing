package com.example.ticketing.auth.otp;

import com.github.benmanes.caffeine.cache.Caffeine;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.ticketing.auth.AuthProperties;

@Configuration
@EnableCaching
@EnableConfigurationProperties(AuthProperties.class)
public class ResetCacheConfiguration {

    @Bean
    CacheManager cacheManager(AuthProperties properties) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.registerCustomCache(OtpStore.CACHE_NAME, Caffeine.newBuilder()
            .expireAfterWrite(properties.otp().ttl())
            .maximumSize(10_000)
            .build());
        cacheManager.registerCustomCache(ResetTicketStore.CACHE_NAME, Caffeine.newBuilder()
            .expireAfterWrite(properties.resetTicket().ttl())
            .maximumSize(10_000)
            .build());
        return cacheManager;
    }
}
