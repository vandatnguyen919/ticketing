package com.example.ticketing.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("users")
public record User(
    @Id Long id,
    String email,
    @Column("display_name") String displayName,
    OAuthProvider provider,
    @Column("provider_id") String providerId,
    @Column("password_hash") String passwordHash
) {
}
