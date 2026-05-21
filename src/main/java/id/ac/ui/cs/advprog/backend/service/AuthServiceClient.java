package id.ac.ui.cs.advprog.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import id.ac.ui.cs.advprog.backend.dto.LoginRequest;
import id.ac.ui.cs.advprog.backend.dto.LoginResponse;
import id.ac.ui.cs.advprog.backend.dto.RegisterRequest;
import id.ac.ui.cs.advprog.backend.dto.RegisterResponse;
import id.ac.ui.cs.advprog.backend.dto.UserSummary;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AuthServiceClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final boolean enabled;
    private final String baseUrl;

    public AuthServiceClient(
        @Value("${microservices.auth.enabled:${AUTH_SERVICE_ENABLED:false}}") boolean enabled,
        @Value("${microservices.auth.base-url:${AUTH_SERVICE_BASE_URL:}}") String baseUrl
    ) {
        this.enabled = enabled;
        this.baseUrl = trimTrailingSlash(baseUrl);
    }

    public boolean isEnabled() {
        return enabled && baseUrl != null && !baseUrl.isBlank();
    }

    public RegisterResponse register(RegisterRequest request) {
        try {
            ensureConfigured();
            return restTemplate.postForObject(baseUrl + "/api/auth/register", request, RegisterResponse.class);
        } catch (HttpStatusCodeException exception) {
            throw toResponseStatusException(exception);
        }
    }

    public LoginResponse login(LoginRequest request) {
        try {
            ensureConfigured();
            return restTemplate.postForObject(baseUrl + "/api/auth/login", request, LoginResponse.class);
        } catch (HttpStatusCodeException exception) {
            throw toResponseStatusException(exception);
        }
    }

    public UserSummary me() {
        try {
            ensureConfigured();
            return restTemplate.exchange(
                baseUrl + "/api/auth/me",
                org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(headersWithAuthorization()),
                UserSummary.class
            ).getBody();
        } catch (HttpStatusCodeException exception) {
            throw toResponseStatusException(exception);
        }
    }

    private HttpHeaders headersWithAuthorization() {
        HttpHeaders headers = new HttpHeaders();
        HttpServletRequest request = currentRequest();
        if (request != null) {
            String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization != null && !authorization.isBlank()) {
                headers.set(HttpHeaders.AUTHORIZATION, authorization);
            }
        }
        return headers;
    }

    private void ensureConfigured() {
        if (!isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Auth service base URL is not configured");
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private ResponseStatusException toResponseStatusException(HttpStatusCodeException exception) {
        return new ResponseStatusException(
            exception.getStatusCode(),
            extractMessage(exception),
            exception
        );
    }

    private String extractMessage(HttpStatusCodeException exception) {
        String body = exception.getResponseBodyAsString();
        if (body != null && !body.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(body);
                JsonNode message = root.get("message");
                if (message != null && !message.asText().isBlank()) {
                    return message.asText();
                }
            } catch (Exception ignored) {
                // Fall through to the HTTP status text.
            }
        }
        return exception.getStatusText();
    }
}
