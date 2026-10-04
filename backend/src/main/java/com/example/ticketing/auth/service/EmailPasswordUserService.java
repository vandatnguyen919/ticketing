package com.example.ticketing.auth.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing.auth.EmailNormalizer;
import com.example.ticketing.auth.dto.LoginRequest;
import com.example.ticketing.auth.dto.RegisterRequest;
import com.example.ticketing.model.OAuthProvider;
import com.example.ticketing.model.User;
import com.example.ticketing.model.UserProfile;
import com.example.ticketing.repository.UserRepository;

@Service
public class EmailPasswordUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public EmailPasswordUserService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        AuthenticationManager authenticationManager
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }

    @Transactional
    public UserProfile register(RegisterRequest request) {
        String email = EmailNormalizer.normalize(request.email());
        String passwordHash = passwordEncoder.encode(request.password());
        if (userRepository.countByEmailIgnoreCase(email) > 0) {
            throw new EmailAlreadyRegisteredException();
        }
        User saved;
        try {
            saved = userRepository.save(new User(
                null,
                email,
                request.displayName().trim(),
                OAuthProvider.EMAIL,
                email,
                passwordHash
            ));
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyRegisteredException();
        }
        return profileOf(saved);
    }

    @Transactional(readOnly = true)
    public UserProfile login(LoginRequest request) {
        String email = EmailNormalizer.normalize(request.email());
        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password())
            );
        } catch (AuthenticationException exception) {
            throw new InvalidCredentialsException();
        }
        User user = userRepository.findByProviderAndProviderId(OAuthProvider.EMAIL, email)
            .orElseThrow(InvalidCredentialsException::new);
        return profileOf(user);
    }

    private UserProfile profileOf(User user) {
        return new UserProfile(
            user.email(),
            user.displayName(),
            user.provider().registrationId(),
            user.providerId()
        );
    }
}
