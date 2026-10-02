package com.example.ticketing.model;

import java.util.Locale;

public enum OAuthProvider {
    GITHUB,
    GOOGLE;

    public static OAuthProvider fromRegistrationId(String registrationId) {
        try {
            return valueOf(registrationId.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported OAuth provider.", exception);
        }
    }

    public String registrationId() {
        return name().toLowerCase(Locale.ROOT);
    }
}
