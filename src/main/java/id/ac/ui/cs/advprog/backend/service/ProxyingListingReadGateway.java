package id.ac.ui.cs.advprog.backend.service;

import id.ac.ui.cs.advprog.backend.config.ListingQueryServiceProperties;
import id.ac.ui.cs.advprog.backend.dto.ListingCategoryNodeResponse;
import id.ac.ui.cs.advprog.backend.dto.ListingDetailResponse;
import id.ac.ui.cs.advprog.backend.dto.ListingResponse;
import id.ac.ui.cs.advprog.backend.model.ListingCategory;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProxyingListingReadGateway implements ListingReadGateway {

    private final RestTemplate restTemplate;
    private final ListingQueryServiceProperties properties;

    public ProxyingListingReadGateway(
        @Qualifier("listingQueryRestTemplate") RestTemplate restTemplate,
        ListingQueryServiceProperties properties
    ) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    @Override
    public List<ListingResponse> list(
        ListingCategory category,
        String keyword,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Instant endingAfter,
        Instant endingBefore
    ) {
        return getList(currentRequestPath("/api/listings"), ListingResponse[].class);
    }

    @Override
    public ListingDetailResponse getById(UUID listingId) {
        return getObject("/api/listings/" + listingId, ListingDetailResponse.class);
    }

    @Override
    public List<ListingCategory> categories() {
        return getList("/api/listings/categories", ListingCategory[].class);
    }

    @Override
    public List<ListingCategoryNodeResponse> categoryTree() {
        return getList("/api/listings/categories/tree", ListingCategoryNodeResponse[].class);
    }

    private <T> T getObject(String path, Class<T> responseType) {
        try {
            ResponseEntity<T> response = restTemplate.getForEntity(buildUri(path), responseType);
            T body = response.getBody();
            if (body == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Listing service returned empty response");
            }
            return body;
        } catch (HttpStatusCodeException exception) {
            throw new ResponseStatusException(exception.getStatusCode(), exception.getStatusText(), exception);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Listing service is unavailable", exception);
        }
    }

    private <T> List<T> getList(String path, Class<T[]> responseType) {
        try {
            ResponseEntity<T[]> response = restTemplate.getForEntity(buildUri(path), responseType);
            T[] body = response.getBody();
            return body == null ? List.of() : Arrays.asList(body);
        } catch (HttpStatusCodeException exception) {
            throw new ResponseStatusException(exception.getStatusCode(), exception.getStatusText(), exception);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Listing service is unavailable", exception);
        }
    }

    private URI buildUri(String path) {
        String baseUrl = requiredBaseUrl();
        return URI.create(baseUrl + path);
    }

    private String requiredBaseUrl() {
        String baseUrl = properties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Listing service base URL is not configured");
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private String currentRequestPath(String defaultPath) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            String query = attrs.getRequest().getQueryString();
            return query == null || query.isBlank() ? defaultPath : defaultPath + "?" + query;
        }
        return defaultPath;
    }
}
