package com.example.ticketing.config;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.ticketing.model.UserProfile;
import com.example.ticketing.service.OAuthProfileException;
import com.example.ticketing.service.OAuthUserProfileService;
import com.example.ticketing.service.UserService;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(OAuth2LoginSuccessHandler.class);
    public static final String USER_PROFILE_SESSION_ATTRIBUTE = "ticketing.oauth.user-profile";

    private final OAuthUserProfileService profileService;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final UserService userService;
    private final String frontendUrl;

    public OAuth2LoginSuccessHandler(
        OAuthUserProfileService profileService,
        OAuth2AuthorizedClientService authorizedClientService,
        UserService userService,
        @Value("${app.frontend-url}") String frontendUrl
    ) {
        this.profileService = profileService;
        this.authorizedClientService = authorizedClientService;
        this.userService = userService;
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void onAuthenticationSuccess(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    ) throws IOException, ServletException {
        if (!(authentication instanceof OAuth2AuthenticationToken oauthAuthentication)) {
            throw new ServletException("Expected an OAuth2 authentication.");
        }

        UserProfile profile;
        try {
            profile = profileService.load(oauthAuthentication);
        } catch (OAuthProfileException exception) {
            LOGGER.warn("Unable to load a verified profile from the OAuth provider.");
            removeProviderCredentials(oauthAuthentication);
            new SecurityContextLogoutHandler().logout(request, response, authentication);
            response.sendRedirect(UriComponentsBuilder.fromUriString(frontendUrl)
                .path("/auth/callback")
                .queryParam("error", "oauth_profile_failed")
                .build()
                .toUriString());
            return;
        }
        profile = userService.syncUser(profile);
        removeProviderCredentials(oauthAuthentication);
        request.getSession(true).setAttribute(USER_PROFILE_SESSION_ATTRIBUTE, profile);

        response.sendRedirect(UriComponentsBuilder.fromUriString(frontendUrl)
            .path("/auth/callback")
            .build()
            .toUriString());
    }

    private void removeProviderCredentials(OAuth2AuthenticationToken authentication) {
        authorizedClientService.removeAuthorizedClient(
            authentication.getAuthorizedClientRegistrationId(),
            authentication.getName()
        );
    }
}
