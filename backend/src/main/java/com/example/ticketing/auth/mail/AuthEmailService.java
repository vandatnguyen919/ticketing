package com.example.ticketing.auth.mail;

public interface AuthEmailService {

    void sendPasswordResetCode(String toEmail, String displayName, String code);
}
