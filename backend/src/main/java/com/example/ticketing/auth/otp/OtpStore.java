package com.example.ticketing.auth.otp;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
public class OtpStore {

    public static final String CACHE_NAME = "auth-password-reset-otp";

    @CachePut(cacheNames = CACHE_NAME, key = "#p0")
    public OtpRecord put(String accountEmail, OtpRecord record) {
        return record;
    }

    @Cacheable(cacheNames = CACHE_NAME, key = "#p0", unless = "#result == null")
    public OtpRecord find(String accountEmail) {
        return null;
    }

    @CacheEvict(cacheNames = CACHE_NAME, key = "#p0")
    public void evict(String accountEmail) {
    }
}
