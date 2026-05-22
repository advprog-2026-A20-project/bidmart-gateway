package id.ac.ui.cs.advprog.backend.service;

import id.ac.ui.cs.advprog.backend.dto.ForgotPasswordRequest;
import id.ac.ui.cs.advprog.backend.dto.LoginRequest;
import id.ac.ui.cs.advprog.backend.dto.LoginResponse;
import id.ac.ui.cs.advprog.backend.dto.RegisterRequest;
import id.ac.ui.cs.advprog.backend.dto.RegisterResponse;
import id.ac.ui.cs.advprog.backend.dto.ResetPasswordRequest;
import id.ac.ui.cs.advprog.backend.dto.UserSummary;
import id.ac.ui.cs.advprog.backend.model.Role;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class AuthServiceClientTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void registerShouldUseTrimmedBaseUrl() {
        AuthServiceClient client = new AuthServiceClient(true, "http://auth-service/");
        MockRestServiceServer server = serverFor(client);

        String userId = UUID.randomUUID().toString();
        server.expect(requestTo("http://auth-service/api/auth/register"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.CREATED)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"id\":\"" + userId + "\",\"email\":\"user@mail.com\",\"role\":\"BUYER\"}"));

        RegisterResponse response = client.register(new RegisterRequest("user@mail.com", "secret123", Role.BUYER));

        assertEquals(UUID.fromString(userId), response.id());
        assertEquals("user@mail.com", response.email());
        assertEquals(Role.BUYER, response.role());
        server.verify();
    }

    @Test
    void meShouldForwardAuthorizationHeaderFromCurrentRequest() {
        AuthServiceClient client = new AuthServiceClient(true, "http://auth-service");
        MockRestServiceServer server = serverFor(client);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer abc.def");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        String userId = UUID.randomUUID().toString();
        server.expect(requestTo("http://auth-service/api/auth/me"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc.def"))
            .andRespond(withStatus(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"id\":\"" + userId + "\",\"email\":\"user@mail.com\",\"role\":\"SELLER\",\"availableBalance\":0,\"heldBalance\":0}"));

        UserSummary me = client.me();

        assertEquals(UUID.fromString(userId), me.id());
        assertEquals("user@mail.com", me.email());
        assertEquals(Role.SELLER, me.role());
        server.verify();
    }

    @Test
    void disabledClientShouldThrowServiceUnavailable() {
        AuthServiceClient client = new AuthServiceClient(false, "http://auth-service");

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> client.login(new LoginRequest("user@mail.com", "secret123"))
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
        assertEquals("Auth service base URL is not configured", exception.getReason());
    }

    @Test
    void forgotPasswordShouldMapErrorMessageFromResponseBody() {
        AuthServiceClient client = new AuthServiceClient(true, "http://auth-service");
        MockRestServiceServer server = serverFor(client);

        server.expect(requestTo("http://auth-service/api/auth/forgot-password"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"message\":\"Email invalid\"}"));

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> client.forgotPassword(new ForgotPasswordRequest("bad-mail"))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Email invalid", exception.getReason());
        server.verify();
    }

    @Test
    void resetPasswordShouldFallbackToStatusTextWhenBodyIsNotJson() {
        AuthServiceClient client = new AuthServiceClient(true, "http://auth-service");
        MockRestServiceServer server = serverFor(client);

        server.expect(requestTo("http://auth-service/api/auth/reset-password"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.TEXT_PLAIN)
                .body("not-json"));

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> client.resetPassword(new ResetPasswordRequest("user@mail.com", "123456", "new-secret"))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Bad Request", exception.getReason());
        server.verify();
    }

    @Test
    void loginShouldDeserializeSuccessResponse() {
        AuthServiceClient client = new AuthServiceClient(true, "http://auth-service");
        MockRestServiceServer server = serverFor(client);

        String userId = UUID.randomUUID().toString();
        server.expect(requestTo("http://auth-service/api/auth/login"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                    {
                      "accessToken":"jwt-token",
                      "user":{
                        "id":"%s",
                        "email":"user@mail.com",
                        "role":"BUYER",
                        "availableBalance":0,
                        "heldBalance":0
                      }
                    }
                    """.formatted(userId)));

        LoginResponse response = client.login(new LoginRequest("user@mail.com", "secret123"));

        assertEquals("jwt-token", response.accessToken());
        assertEquals(UUID.fromString(userId), response.user().id());
        server.verify();
    }

    private MockRestServiceServer serverFor(AuthServiceClient client) {
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        return MockRestServiceServer.bindTo(restTemplate).build();
    }
}
