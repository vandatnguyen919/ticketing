package com.example.ticketing.controller;

import java.util.Map;
import java.time.Duration;
import java.time.Instant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.ticketing.config.OAuth2LoginSuccessHandler;
import com.example.ticketing.model.AuthResponse;
import com.example.ticketing.model.UserProfile;
import com.example.ticketing.service.JwtService;

@RestController
@RequestMapping(path = "/api/{version}/auth", version = "1.0")
public class AuthController {

    private final JwtService jwtService;
    private final String cookieName;
    private final String cookiePath;
    private final boolean cookieSecure;
    private final String cookieSameSite;

    public AuthController(
        JwtService jwtService,
        @Value("${app.security.cookie.name}") String cookieName,
        @Value("${app.security.cookie.path}") String cookiePath,
        @Value("${app.security.cookie.secure}") boolean cookieSecure,
        @Value("${app.security.cookie.same-site}") String cookieSameSite
    ) {
        this.jwtService = jwtService;
        this.cookieName = cookieName;
        this.cookiePath = cookiePath;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
    }

    @GetMapping("/csrf")
    public ResponseEntity<Void> csrfToken(CsrfToken csrfToken) {
        csrfToken.getToken();
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/exchange-session", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> exchangeOAuthSession(
        Authentication authentication,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        if (!(authentication instanceof OAuth2AuthenticationToken oauthAuthentication)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Complete provider sign-in first.");
        }

        var session = request.getSession(false);
        Object sessionProfile = session == null
            ? null
            : session.getAttribute(OAuth2LoginSuccessHandler.USER_PROFILE_SESSION_ATTRIBUTE);
        if (!(sessionProfile instanceof UserProfile profile)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "The sign-in session has expired.");
        }

        JwtService.IssuedToken token = jwtService.generateToken(profile);
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        ResponseCookie cookie = ResponseCookie.from(cookieName, token.value())
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite(cookieSameSite)
            .path(cookiePath)
            .maxAge(Duration.between(Instant.now(), token.expiresAt()))
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.ok(new AuthResponse(profile));
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfile> currentUser(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A valid authentication cookie is required.");
        }
        return ResponseEntity.ok(new UserProfile(
            jwt.getSubject(),
            jwt.getClaimAsString("name"),
            jwt.getClaimAsString("provider"),
            jwt.getClaimAsString("providerId")
        ));
    }

    @GetMapping("/providers")
    public Map<String, String> providers() {
        return Map.of(
            "github", "/oauth2/authorization/github",
            "google", "/oauth2/authorization/google"
        );
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
        Authentication authentication,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        if (authentication != null) {
            new SecurityContextLogoutHandler().logout(request, response, authentication);
        }
        ResponseCookie expiredCookie = ResponseCookie.from(cookieName, "")
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite(cookieSameSite)
            .path(cookiePath)
            .maxAge(Duration.ZERO)
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie.toString());
        return ResponseEntity.noContent().build();
    }
}
