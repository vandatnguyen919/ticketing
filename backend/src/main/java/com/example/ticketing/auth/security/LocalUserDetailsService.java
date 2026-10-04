package com.example.ticketing.auth.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.example.ticketing.auth.EmailNormalizer;
import com.example.ticketing.model.OAuthProvider;
import com.example.ticketing.model.User;
import com.example.ticketing.repository.UserRepository;

@Service
public class LocalUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public LocalUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        User user = userRepository.findByProviderAndProviderId(OAuthProvider.EMAIL, EmailNormalizer.normalize(email))
            .orElseThrow(() -> new UsernameNotFoundException("Unknown account."));
        if (user.passwordHash() == null) {
            throw new UsernameNotFoundException("Unknown account.");
        }
        return org.springframework.security.core.userdetails.User
            .withUsername(user.email())
            .password(user.passwordHash())
            .roles("USER")
            .build();
    }
}
