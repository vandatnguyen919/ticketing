package com.example.ticketing.auth;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.Cookie;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
    "spring.sql.init.mode=never",
    "spring.cloud.vault.token=test-vault-token",
    "oauth.github.client-id=test-github-client",
    "oauth.github.client-secret=test-github-secret",
    "oauth.google.client-id=test-google-client",
    "oauth.google.client-secret=test-google-secret",
    "security.jwt-secret=01234567890123456789012345678901",
    "app.security.cookie.secure=true",
    "app.auth.mail.from=ticketing@localhost"
})
@AutoConfigureMockMvc
class PasswordResetFlowIntegrationTests {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Container
    private static final GenericContainer<?> mailpit = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.31.3"))
        .withExposedPorts(1025, 8025);

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.mail.host", mailpit::getHost);
        registry.add("spring.mail.port", () -> mailpit.getMappedPort(1025));
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void resetsAPasswordThroughTheEmailedCodeAndSignsInWithIt() throws Exception {
        Cookie csrf = csrfCookie();

        MvcResult registered = mockMvc.perform(post("/api/v1/auth/register")
                .cookie(csrf)
                .header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dana@example.com\",\"password\":\"the-first-password\",\"displayName\":\"Dana\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.user.email").value("dana@example.com"))
            .andExpect(jsonPath("$.token").doesNotExist())
            .andExpect(header().string("Set-Cookie", allOf(
                containsString("ticketing-token="),
                containsString("Path=/api"),
                containsString("Secure"),
                containsString("HttpOnly"),
                containsString("SameSite=Lax")
            )))
            .andReturn();
        Cookie firstCredential = registered.getResponse().getCookie("ticketing-token");
        assertNotNull(firstCredential);
        assertTrue(firstCredential.isHttpOnly());

        signIn("dana@example.com", "the-first-password").andExpect(status().isOk());

        String unknownResponse = mockMvc.perform(resetRequest("nobody@example.com", csrf))
            .andExpect(status().isAccepted())
            .andReturn().getResponse().getContentAsString();
        Thread.sleep(1500);
        assertEquals(0, messageCount(), "An unknown account must not produce mail");

        String knownResponse = mockMvc.perform(resetRequest("dana@example.com", csrf))
            .andExpect(status().isAccepted())
            .andReturn().getResponse().getContentAsString();
        assertEquals(unknownResponse, knownResponse, "Both answers must look identical");
        mockMvc.perform(resetRequest("dana@example.com", csrf)).andExpect(status().isAccepted());

        String emailText = waitForMessageText();
        assertEquals(1, messageCount(), "The cooldown must suppress a second code for the same account");
        Matcher codeMatcher = Pattern.compile("(\\d{6})").matcher(emailText);
        assertTrue(codeMatcher.find(), "The email should carry a six digit code: " + emailText);
        String code = codeMatcher.group(1);

        mockMvc.perform(verifyRequest("dana@example.com", "000000", csrf))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("invalid_otp"));

        MvcResult verified = mockMvc.perform(verifyRequest("dana@example.com", code, csrf))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.expiresInSeconds").value(300))
            .andReturn();
        String ticket = JSON.readTree(verified.getResponse().getContentAsString()).get("resetTicket").asText();

        MvcResult siblingVerify = mockMvc.perform(verifyRequest("dana@example.com", code, csrf))
            .andExpect(status().isOk())
            .andReturn();
        String siblingTicket = JSON.readTree(siblingVerify.getResponse().getContentAsString())
            .get("resetTicket").asText();

        mockMvc.perform(confirmRequest(ticket, "short", csrf))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("validation_error"));

        mockMvc.perform(confirmRequest(ticket, "a-brand-new-password", csrf))
            .andExpect(status().isNoContent());

        mockMvc.perform(confirmRequest(ticket, "a-second-password-1", csrf))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("reset_ticket_invalid"));

        // A ticket minted earlier must not survive the completed reset.
        mockMvc.perform(confirmRequest(siblingTicket, "a-third-password-1", csrf))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("reset_ticket_invalid"));

        MvcResult refused = signIn("dana@example.com", "the-first-password")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("invalid_credentials"))
            .andReturn();
        assertTrue(refused.getResponse().getHeader("Set-Cookie") == null, "A failed login must not issue a cookie");

        MvcResult accepted = signIn("dana@example.com", "a-brand-new-password")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.email").value("dana@example.com"))
            .andExpect(jsonPath("$.token").doesNotExist())
            .andReturn();
        assertNotNull(accepted.getResponse().getCookie("ticketing-token"));
    }

    private org.springframework.test.web.servlet.ResultActions signIn(String email, String password) throws Exception {
        Cookie csrf = csrfCookie();
        return mockMvc.perform(post("/api/v1/auth/login")
            .cookie(csrf)
            .header("X-XSRF-TOKEN", csrf.getValue())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private org.springframework.test.web.servlet.RequestBuilder resetRequest(String email, Cookie csrf) {
        return post("/api/v1/auth/password-reset/request")
            .cookie(csrf)
            .header("X-XSRF-TOKEN", csrf.getValue())
            .contentType(MediaType.APPLICATION_JSON)
            .content(resetRequestBody(email));
    }

    private String resetRequestBody(String email) {
        return "{\"email\":\"" + email + "\"}";
    }

    private org.springframework.test.web.servlet.RequestBuilder verifyRequest(String email, String code, Cookie csrf) {
        return post("/api/v1/auth/password-reset/verify")
            .cookie(csrf)
            .header("X-XSRF-TOKEN", csrf.getValue())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}");
    }

    private org.springframework.test.web.servlet.RequestBuilder confirmRequest(String ticket, String password, Cookie csrf) {
        return post("/api/v1/auth/password-reset/confirm")
            .cookie(csrf)
            .header("X-XSRF-TOKEN", csrf.getValue())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"resetTicket\":\"" + ticket + "\",\"newPassword\":\"" + password + "\"}");
    }

    private Cookie csrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf")).andReturn();
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        if (cookie == null) {
            throw new AssertionError("The CSRF bootstrap endpoint did not issue an XSRF-TOKEN cookie.");
        }
        return cookie;
    }

    private int messageCount() throws Exception {
        return mailpitJson("/api/v1/messages").get("total").asInt();
    }

    private String waitForMessageText() throws Exception {
        for (int attempt = 0; attempt < 60; attempt++) {
            JsonNode messages = mailpitJson("/api/v1/messages");
            if (messages.get("total").asInt() >= 1) {
                String id = messages.get("messages").get(0).get("ID").asText();
                return mailpitJson("/api/v1/message/" + id).get("Text").asText();
            }
            Thread.sleep(250);
        }
        throw new AssertionError("No reset email arrived in Mailpit.");
    }

    private JsonNode mailpitJson(String path) throws Exception {
        String url = "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025) + path;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), "Mailpit API " + path + " failed");
        return JSON.readTree(response.body());
    }
}
