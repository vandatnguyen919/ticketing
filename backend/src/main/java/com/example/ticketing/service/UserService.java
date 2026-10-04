package com.example.ticketing.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing.model.OAuthProvider;
import com.example.ticketing.model.User;
import com.example.ticketing.model.UserProfile;
import com.example.ticketing.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserProfile syncUser(UserProfile profile) {
        OAuthProvider provider = OAuthProvider.fromRegistrationId(profile.provider());
        User user = userRepository.findByProviderAndProviderId(provider, profile.providerId())
            .map(existing -> new User(
                existing.id(),
                profile.email(),
                profile.name(),
                provider,
                profile.providerId(),
                existing.passwordHash()
            ))
            .orElseGet(() -> new User(
                null,
                profile.email(),
                profile.name(),
                provider,
                profile.providerId(),
                null
            ));
        User saved = userRepository.save(user);
        return new UserProfile(
            saved.email(),
            saved.displayName(),
            saved.provider().registrationId(),
            saved.providerId()
        );
    }
}
