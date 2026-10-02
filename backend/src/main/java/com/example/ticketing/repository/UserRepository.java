package com.example.ticketing.repository;

import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

import com.example.ticketing.model.OAuthProvider;
import com.example.ticketing.model.User;

public interface UserRepository extends CrudRepository<User, Long> {

    Optional<User> findByProviderAndProviderId(OAuthProvider provider, String providerId);
}
