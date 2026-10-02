package com.example.ticketing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OAuthUserProfileServiceTests {

    @Test
    void loadsVerifiedGoogleProfile() {
        OAuth2AuthenticationToken authentication = authentication(
            "google",
            Map.of(
                "sub", "google-user-1",
                "email", "person@example.com",
                "email_verified", true,
                "name", "Example Person"
            ),
            "sub"
        );

        var profile = profileService(mock(OAuth2AuthorizedClientService.class)).load(authentication);

        assertEquals("person@example.com", profile.email());
        assertEquals("Example Person", profile.name());
        assertEquals("google", profile.provider());
        assertEquals("google-user-1", profile.providerId());
    }

    @Test
    void rejectsUnverifiedGoogleEmail() {
        OAuth2AuthenticationToken authentication = authentication(
            "google",
            Map.of(
                "sub", "google-user-1",
                "email", "person@example.com",
                "email_verified", false
            ),
            "sub"
        );

        assertThrows(
            OAuthProfileException.class,
            () -> profileService(mock(OAuth2AuthorizedClientService.class)).load(authentication)
        );
    }

    @Test
    void rejectsUnknownProvider() {
        OAuth2AuthenticationToken authentication = authentication(
            "unknown",
            Map.of("sub", "provider-user-1", "email", "person@example.com"),
            "sub"
        );

        assertThrows(
            OAuthProfileException.class,
            () -> profileService(mock(OAuth2AuthorizedClientService.class)).load(authentication)
        );
    }

    @Test
    void rejectsGitHubProfileWhenNoVerifiedEmailIsAvailable() {
        OAuth2AuthenticationToken authentication = authentication(
            "github",
            Map.of("id", 123L, "login", "example-user"),
            "login"
        );

        assertThrows(
            OAuthProfileException.class,
            () -> profileService(mock(OAuth2AuthorizedClientService.class)).load(authentication)
        );
    }

    @Test
    void rejectsGitHubProfileWithoutStableProviderId() {
        OAuth2AuthenticationToken authentication = authentication(
            "github",
            Map.of("login", "example-user"),
            "login"
        );

        assertThrows(
            OAuthProfileException.class,
            () -> profileService(mock(OAuth2AuthorizedClientService.class)).load(authentication)
        );
    }

    @Test
    void loadsVerifiedGitHubProfileAndNumericProviderId() {
        OAuth2AuthorizedClientService authorizedClientService = mock(OAuth2AuthorizedClientService.class);
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        OAuth2AuthenticationToken authentication = authentication(
            "github",
            Map.of("id", 123L, "login", "example-user", "name", "Example Person"),
            "login"
        );
        ClientRegistration registration = ClientRegistration.withRegistrationId("github")
            .clientId("client")
            .clientSecret("secret")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost/login/oauth2/code/github")
            .authorizationUri("https://github.com/login/oauth/authorize")
            .tokenUri("https://github.com/login/oauth/access_token")
            .userInfoUri("https://api.github.com/user")
            .userNameAttributeName("login")
            .clientName("GitHub")
            .build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "access-token",
            Instant.now(),
            Instant.now().plusSeconds(3600)
        );
        when(authorizedClientService.loadAuthorizedClient("github", "example-user"))
            .thenReturn(new OAuth2AuthorizedClient(registration, "example-user", accessToken));
        server.expect(requestTo("https://api.github.com/user/emails"))
            .andRespond(withSuccess(
                """
                [{"email":"person@example.com","primary":true,"verified":true}]
                """,
                MediaType.APPLICATION_JSON
            ));

        var profile = new OAuthUserProfileService(authorizedClientService, restClientBuilder).load(authentication);

        assertEquals("person@example.com", profile.email());
        assertEquals("github", profile.provider());
        assertEquals("123", profile.providerId());
        server.verify();
    }

    private OAuth2AuthenticationToken authentication(
        String registrationId,
        Map<String, Object> attributes,
        String nameAttributeKey
    ) {
        var authorities = Set.of(new SimpleGrantedAuthority("ROLE_USER"));
        var principal = new DefaultOAuth2User(authorities, attributes, nameAttributeKey);
        return new OAuth2AuthenticationToken(principal, authorities, registrationId);
    }

    private OAuthUserProfileService profileService(OAuth2AuthorizedClientService authorizedClientService) {
        return new OAuthUserProfileService(authorizedClientService, RestClient.builder());
    }
}
