package com.example.ticketing.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;

import jakarta.servlet.ServletException;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import com.example.ticketing.model.UserProfile;
import com.example.ticketing.service.OAuthProfileException;
import com.example.ticketing.service.OAuthUserProfileService;
import com.example.ticketing.service.UserService;

class OAuth2LoginHandlerTests {

    private final OAuthUserProfileService profileService = mock(OAuthUserProfileService.class);
    private final OAuth2AuthorizedClientService authorizedClientService = mock(OAuth2AuthorizedClientService.class);
    private final UserService userService = mock(UserService.class);
    private final OAuth2AuthenticationToken authentication = googleAuthentication();
    private final UserProfile profile = new UserProfile("person@example.com", "Person", "google", "google-subject");

    @Test
    void persistsTheVerifiedProviderIdentityAndStoresOnlyTheProfileForExchange() throws Exception {
        when(profileService.load(authentication)).thenReturn(profile);
        when(userService.syncUser(profile)).thenReturn(profile);
        OAuth2LoginSuccessHandler handler = new OAuth2LoginSuccessHandler(
            profileService,
            authorizedClientService,
            userService,
            "http://localhost:5173"
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        assertEquals("http://localhost:5173/auth/callback", response.getRedirectedUrl());
        assertSame(
            profile,
            request.getSession(false).getAttribute(OAuth2LoginSuccessHandler.USER_PROFILE_SESSION_ATTRIBUTE)
        );
        verify(userService).syncUser(profile);
        verify(authorizedClientService).removeAuthorizedClient("google", authentication.getName());
    }

    @Test
    void redirectsProfileFailuresBackToTheSpaWithoutCompletingLogin() throws Exception {
        when(profileService.load(authentication)).thenThrow(new OAuthProfileException("invalid provider profile"));
        OAuth2LoginSuccessHandler handler = new OAuth2LoginSuccessHandler(
            profileService,
            authorizedClientService,
            userService,
            "http://localhost:5173"
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        assertEquals(
            "http://localhost:5173/auth/callback?error=oauth_profile_failed",
            response.getRedirectedUrl()
        );
        verify(authorizedClientService).removeAuthorizedClient("google", authentication.getName());
    }

    @Test
    void redirectsOAuthProviderFailuresToTheSpa() throws Exception {
        OAuth2LoginFailureHandler handler = new OAuth2LoginFailureHandler("http://localhost:5173");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(
            new MockHttpServletRequest(),
            response,
            new OAuth2AuthenticationException(new OAuth2Error("access_denied"))
        );

        assertEquals("http://localhost:5173/auth/callback?error=oauth_failed", response.getRedirectedUrl());
    }

    private OAuth2AuthenticationToken googleAuthentication() {
        var authorities = Set.of(new SimpleGrantedAuthority("ROLE_USER"));
        var principal = new DefaultOAuth2User(authorities, Map.of("sub", "google-subject"), "sub");
        return new OAuth2AuthenticationToken(principal, authorities, "google");
    }
}
