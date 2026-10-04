package com.example.ticketing.repository;

import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import com.example.ticketing.model.OAuthProvider;
import com.example.ticketing.model.User;

public interface UserRepository extends CrudRepository<User, Long> {

    Optional<User> findByProviderAndProviderId(OAuthProvider provider, String providerId);

    @Query("SELECT COUNT(*) FROM users WHERE LOWER(email) = LOWER(:email)")
    long countByEmailIgnoreCase(@Param("email") String email);
}
