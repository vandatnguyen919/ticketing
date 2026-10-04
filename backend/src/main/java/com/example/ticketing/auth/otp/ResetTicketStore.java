package com.example.ticketing.auth.otp;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
public class ResetTicketStore {

    public static final String CACHE_NAME = "auth-password-reset-ticket";

    @CachePut(cacheNames = CACHE_NAME, key = "#p0")
    public ResetTicketRecord put(String ticketHash, ResetTicketRecord record) {
        return record;
    }

    @Cacheable(cacheNames = CACHE_NAME, key = "#p0", unless = "#result == null")
    public ResetTicketRecord find(String ticketHash) {
        return null;
    }

    @CacheEvict(cacheNames = CACHE_NAME, key = "#p0")
    public void evict(String ticketHash) {
    }
}
