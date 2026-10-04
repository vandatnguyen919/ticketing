package com.example.ticketing.auth.web;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.ticketing.auth.dto.PasswordResetConfirmRequest;
import com.example.ticketing.auth.dto.PasswordResetRequest;
import com.example.ticketing.auth.dto.PasswordResetRequestResponse;
import com.example.ticketing.auth.dto.PasswordResetVerifyRequest;
import com.example.ticketing.auth.dto.PasswordResetVerifyResponse;
import com.example.ticketing.auth.service.PasswordResetService;

@RestController
@RequestMapping(path = "/api/{version}/auth", version = "1.0")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @PostMapping(value = "/password-reset/request", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PasswordResetRequestResponse requestReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.requestReset(request.email());
        return PasswordResetRequestResponse.accepted();
    }

    @PostMapping(value = "/password-reset/verify", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PasswordResetVerifyResponse verifyCode(@Valid @RequestBody PasswordResetVerifyRequest request) {
        return passwordResetService.verify(request.email(), request.code());
    }

    @PostMapping(value = "/password-reset/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirm(request.resetTicket(), request.newPassword());
    }
}
