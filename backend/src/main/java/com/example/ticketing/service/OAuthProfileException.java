package com.example.ticketing.service;

public class OAuthProfileException extends RuntimeException {

    public OAuthProfileException(String message) {
        super(message);
    }

    public OAuthProfileException(String message, Throwable cause) {
        super(message, cause);
    }
}
