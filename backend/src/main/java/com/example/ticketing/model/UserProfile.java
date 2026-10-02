package com.example.ticketing.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record UserProfile(String email, String name, String provider, @JsonIgnore String providerId) {
}
