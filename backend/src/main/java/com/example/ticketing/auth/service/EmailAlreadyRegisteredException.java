package com.example.ticketing.auth.service;

public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException() {
        super("An account already exists for this email address.");
    }
}
