package com.example.ticketing.service;

import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.example.ticketing.model.UserProfile;

@Service
public class OAuthUserProfileService {

    private final OAuth2AuthorizedClientService authorizedClientService;
    private final RestClient restClient;

    public OAuthUserProfileService(
        OAuth2AuthorizedClientService authorizedClientService,
        RestClient.Builder restClientBuilder
    ) {
        this.authorizedClientService = authorizedClientService;
        this.restClient = restClientBuilder.build();
    }

    public UserProfile load(OAuth2AuthenticationToken authentication) {
        String provider = authentication.getAuthorizedClientRegistrationId();
        Map<String, Object> attributes = authentication.getPrincipal().getAttributes();

        String providerId = switch (provider) {
            case "github" -> githubId(attributes);
            case "google" -> googleId(attributes);
            default -> throw new OAuthProfileException("Unsupported OAuth provider.");
        };
        String email = switch (provider) {
            case "github" -> githubEmail(authentication);
            case "google" -> googleEmail(attributes);
            default -> throw new OAuthProfileException("Unsupported OAuth provider.");
        };

        String name = firstText(attributes, "name", "login", "preferred_username");
        if (!StringUtils.hasText(name)) {
            name = email;
        }

        return new UserProfile(email, name, provider, providerId);
    }

    private String googleId(Map<String, Object> attributes) {
        String subject = stringAttribute(attributes, "sub");
        if (!StringUtils.hasText(subject)) {
            throw new OAuthProfileException("The Google account did not provide a stable subject.");
        }
        return subject;
    }

    private String githubId(Map<String, Object> attributes) {
        Object id = attributes.get("id");
        if (id instanceof Number number && number.longValue() > 0) {
            return Long.toString(number.longValue());
        }
        if (id instanceof String text && text.matches("[0-9]+")) {
            return text;
        }
        throw new OAuthProfileException("The GitHub account did not provide a stable numeric ID.");
    }

    private String googleEmail(Map<String, Object> attributes) {
        Object verified = attributes.get("email_verified");
        String email = stringAttribute(attributes, "email");
        if (!Boolean.TRUE.equals(verified) || !StringUtils.hasText(email)) {
            throw new OAuthProfileException("The Google account must have a verified email address.");
        }
        return email;
    }

    private String githubEmail(OAuth2AuthenticationToken authentication) {
        OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(
            "github",
            authentication.getName()
        );
        if (authorizedClient == null || authorizedClient.getAccessToken() == null) {
            throw new OAuthProfileException("Unable to load the verified GitHub email address.");
        }

        try {
            List<GitHubEmail> emails = restClient.get()
                .uri("https://api.github.com/user/emails")
                .headers(headers -> {
                    headers.setBearerAuth(authorizedClient.getAccessToken().getTokenValue());
                    headers.set("Accept", "application/vnd.github+json");
                })
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

            if (emails == null) {
                throw new OAuthProfileException("GitHub did not return an email address.");
            }
            return emails.stream()
                .filter(address -> address.primary() && address.verified())
                .map(GitHubEmail::email)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElseThrow(() -> new OAuthProfileException(
                    "The GitHub account must have a verified primary email address."
                ));
        } catch (RestClientException exception) {
            throw new OAuthProfileException("Unable to retrieve the verified GitHub email address.", exception);
        }
    }

    private String firstText(Map<String, Object> attributes, String... keys) {
        for (String key : keys) {
            String value = stringAttribute(attributes, key);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String stringAttribute(Map<String, Object> attributes, String key) {
        Object value = attributes.get(key);
        return value instanceof String text ? text : null;
    }

    private record GitHubEmail(String email, boolean primary, boolean verified) {
    }
}
