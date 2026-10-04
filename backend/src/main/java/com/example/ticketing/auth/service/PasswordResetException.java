package com.example.ticketing.auth.service;

public class PasswordResetException extends RuntimeException {

    public static final String INVALID_OTP = "invalid_otp";
    public static final String OTP_EXPIRED = "otp_expired";
    public static final String OTP_LOCKED = "otp_locked";
    public static final String RESET_TICKET_INVALID = "reset_ticket_invalid";

    private final String error;

    public PasswordResetException(String error, String message) {
        super(message);
        this.error = error;
    }

    public String error() {
        return error;
    }
}
