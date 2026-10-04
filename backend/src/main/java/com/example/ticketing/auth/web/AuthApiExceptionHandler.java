package com.example.ticketing.auth.web;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.ticketing.auth.dto.ApiError;
import com.example.ticketing.auth.service.EmailAlreadyRegisteredException;
import com.example.ticketing.auth.service.InvalidCredentialsException;
import com.example.ticketing.auth.service.PasswordResetException;

@RestControllerAdvice(basePackages = "com.example.ticketing.auth.web")
public class AuthApiExceptionHandler {

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ApiError> emailAlreadyRegistered(EmailAlreadyRegisteredException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ApiError("email_taken", exception.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> invalidCredentials(InvalidCredentialsException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ApiError("invalid_credentials", exception.getMessage()));
    }

    @ExceptionHandler(PasswordResetException.class)
    public ResponseEntity<ApiError> passwordReset(PasswordResetException exception) {
        return ResponseEntity.badRequest()
            .body(new ApiError(exception.error(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalidRequest(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(new ApiError("validation_error", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadableRequest(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(new ApiError("validation_error", "The request body is invalid."));
    }
}
