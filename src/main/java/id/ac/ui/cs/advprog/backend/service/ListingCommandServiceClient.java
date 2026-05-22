package id.ac.ui.cs.advprog.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import id.ac.ui.cs.advprog.backend.config.ListingQueryServiceProperties;
import id.ac.ui.cs.advprog.backend.dto.ListingBidValidationResponse;
import id.ac.ui.cs.advprog.backend.dto.ListingCreateRequest;
import id.ac.ui.cs.advprog.backend.dto.ListingDetailResponse;
import id.ac.ui.cs.advprog.backend.dto.ListingResponse;
import id.ac.ui.cs.advprog.backend.dto.ListingUpdateRequest;
import id.ac.ui.cs.advprog.backend.dto.PublicSellerProfileResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ListingCommandServiceClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ListingQueryServiceProperties properties;

    public ListingCommandServiceClient(
        @Qualifier("serviceCommandRestTemplate") RestTemplate restTemplate,
        ListingQueryServiceProperties properties
    ) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public ListingResponse create(ListingCreateRequest request) {
        try {
            return restTemplate.exchange(
                buildUri("/api/listings"),
                HttpMethod.POST,
                new HttpEntity<>(request, headersWithAuthorization()),
                ListingResponse.class
            ).getBody();
        } catch (HttpStatusCodeException exception) {
            throw toResponseStatusException(exception);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Listing service is unavailable", exception);
        }
    }

    public ListingDetailResponse update(UUID listingId, ListingUpdateRequest request) {
        try {
            return restTemplate.exchange(
                buildUri("/api/listings/" + listingId),
                HttpMethod.PUT,
                new HttpEntity<>(request, headersWithAuthorization()),
                ListingDetailResponse.class
            ).getBody();
        } catch (HttpStatusCodeException exception) {
            throw toResponseStatusException(exception);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Listing service is unavailable", exception);
        }
    }

    public ListingDetailResponse cancel(UUID listingId) {
        try {
            return restTemplate.exchange(
                buildUri("/api/listings/" + listingId),
                HttpMethod.DELETE,
                new HttpEntity<>(headersWithAuthorization()),
                ListingDetailResponse.class
            ).getBody();
        } catch (HttpStatusCodeException exception) {
            throw toResponseStatusException(exception);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Listing service is unavailable", exception);
        }
    }

    public ListingBidValidationResponse validateForBid(UUID listingId) {
        try {
            return restTemplate.getForObject(
                buildUri("/api/listings/" + listingId + "/validation"),
                ListingBidValidationResponse.class
            );
        } catch (HttpStatusCodeException exception) {
            throw toResponseStatusException(exception);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Listing service is unavailable", exception);
        }
    }

    public PublicSellerProfileResponse publicProfile(UUID userId) {
        try {
            return restTemplate.getForObject(
                buildUri("/api/users/" + userId + "/public-profile"),
                PublicSellerProfileResponse.class
            );
        } catch (HttpStatusCodeException exception) {
            throw toResponseStatusException(exception);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Listing service is unavailable", exception);
        }
    }

    private URI buildUri(String path) {
        String baseUrl = properties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Listing service base URL is not configured");
        }
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return URI.create(normalizedBaseUrl + path);
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

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    private ResponseStatusException toResponseStatusException(HttpStatusCodeException exception) {
        return new ResponseStatusException(exception.getStatusCode(), extractMessage(exception), exception);
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
