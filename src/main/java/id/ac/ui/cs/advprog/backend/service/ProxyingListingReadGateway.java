package id.ac.ui.cs.advprog.backend.service;

import id.ac.ui.cs.advprog.backend.config.ListingQueryServiceProperties;
import id.ac.ui.cs.advprog.backend.dto.ListingCategoryNodeResponse;
import id.ac.ui.cs.advprog.backend.dto.ListingDetailResponse;
import id.ac.ui.cs.advprog.backend.dto.ListingResponse;
import id.ac.ui.cs.advprog.backend.model.ListingCategory;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProxyingListingReadGateway implements ListingReadGateway {

    private static final Logger logger = LoggerFactory.getLogger(ProxyingListingReadGateway.class);

    private final ListingService listingService;
    private final RestTemplate restTemplate;
    private final ListingQueryServiceProperties properties;

    private record InvocationResult<T>(HttpStatus status, T body, Exception exception, long durationMs) {
        boolean isSuccess() {
            return exception == null;
        }
    }

    public ProxyingListingReadGateway(
        ListingService listingService,
        @Qualifier("listingQueryRestTemplate") RestTemplate restTemplate,
        ListingQueryServiceProperties properties
    ) {
        this.listingService = listingService;
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    @Override
    public List<ListingResponse> list(
        Pageable pageable,
        ListingCategory category,
        String keyword,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Instant endingAfter,
        Instant endingBefore
    ) {
        return resolveInvocation(
            "GET " + currentOperationPath("/api/listings"),
            () -> invokeLocal(() -> listingService.getAllListings(
                pageable,
                category,
                keyword,
                minPrice,
                maxPrice,
                endingAfter,
                endingBefore
            )),
            () -> invokeRemoteList(
                currentRemotePath("/api/listings"),
                ListingResponse[].class
            ),
            "Listing query service is unavailable"
        );
    }

    @Override
    public ListingDetailResponse getById(UUID listingId) {
        String path = "/api/listings/" + listingId;
        return resolveInvocation(
            "GET " + path,
            () -> invokeLocal(() -> listingService.getListingDetail(listingId)),
            () -> invokeRemoteObject(path, ListingDetailResponse.class),
            "Listing not found"
        );
    }

    @Override
    public List<ListingCategory> categories() {
        return resolveInvocation(
            "GET /api/listings/categories",
            () -> invokeLocal(() -> Arrays.stream(ListingCategory.values()).toList()),
            () -> invokeRemoteList("/api/listings/categories", ListingCategory[].class),
            "Listing query service is unavailable"
        );
    }

    @Override
    public List<ListingCategoryNodeResponse> categoryTree() {
        return resolveInvocation(
            "GET /api/listings/categories/tree",
            () -> invokeLocal(listingService::getCategoryTree),
            () -> invokeRemoteList("/api/listings/categories/tree", ListingCategoryNodeResponse[].class),
            "Listing query service is unavailable"
        );
    }

    private <T> T resolveInvocation(
        String operation,
        Supplier<InvocationResult<T>> localInvocation,
        Supplier<InvocationResult<T>> remoteInvocation,
        String remoteFailureMessage
    ) {
        ListingQueryServiceProperties.RolloutMode rolloutMode = resolveRolloutMode();
        if (rolloutMode == ListingQueryServiceProperties.RolloutMode.DISABLED || !shouldAttemptRemote()) {
            return unwrap(localInvocation.get(), remoteFailureMessage);
        }

        if (rolloutMode == ListingQueryServiceProperties.RolloutMode.SHADOW) {
            logProxyHit("shadow-hit", rolloutMode, operation);
            InvocationResult<T> localResult = localInvocation.get();
            InvocationResult<T> remoteResult = remoteInvocation.get();
            logShadowResult(operation, localResult, remoteResult);
            return unwrap(localResult, remoteFailureMessage);
        }

        if (rolloutMode == ListingQueryServiceProperties.RolloutMode.PERCENT && !shouldUseRemoteForCurrentRequest()) {
            return unwrap(localInvocation.get(), remoteFailureMessage);
        }

        logProxyHit("remote-hit", rolloutMode, operation);
        InvocationResult<T> remoteResult = remoteInvocation.get();
        if (remoteResult.isSuccess()) {
            logger.info(
                "listing-query.proxy event=remote-success rolloutMode={} operation={} remoteStatus={} remoteDurationMs={}",
                rolloutMode,
                operation,
                remoteResult.status(),
                remoteResult.durationMs()
            );
            return remoteResult.body();
        }

        if (properties.isFailOpen()) {
            logger.warn(
                "listing-query.proxy event=fallback rolloutMode={} operation={} remoteStatus={} remoteDurationMs={} fallbackReason={} fallbackMessage={}",
                rolloutMode,
                operation,
                remoteResult.status(),
                remoteResult.durationMs(),
                fallbackReason(remoteResult.exception()),
                fallbackMessage(remoteResult.exception())
            );
            InvocationResult<T> localResult = localInvocation.get();
            if (!Objects.equals(remoteResult.status(), localResult.status())) {
                logger.warn(
                    "listing-query.proxy event=status-mismatch rolloutMode={} operation={} remoteStatus={} localStatus={} remoteDurationMs={} localDurationMs={}",
                    rolloutMode,
                    operation,
                    remoteResult.status(),
                    localResult.status(),
                    remoteResult.durationMs(),
                    localResult.durationMs()
                );
            }
            return unwrap(localResult, remoteFailureMessage);
        }

        throw toResponseStatusException(remoteResult, remoteFailureMessage);
    }

    private <T> InvocationResult<T> invokeLocal(Supplier<T> localCall) {
        long startedAt = System.currentTimeMillis();
        try {
            return new InvocationResult<>(HttpStatus.OK, localCall.get(), null, System.currentTimeMillis() - startedAt);
        } catch (ResponseStatusException exception) {
            return new InvocationResult<>(
                HttpStatus.valueOf(exception.getStatusCode().value()),
                null,
                exception,
                System.currentTimeMillis() - startedAt
            );
        } catch (Exception exception) {
            return new InvocationResult<>(
                HttpStatus.INTERNAL_SERVER_ERROR,
                null,
                exception,
                System.currentTimeMillis() - startedAt
            );
        }
    }

    private <T> InvocationResult<T> invokeRemoteObject(String path, Class<T> responseType) {
        long startedAt = System.currentTimeMillis();
        try {
            ResponseEntity<T> response = restTemplate.getForEntity(buildUri(path), responseType);
            if (response.getBody() == null) {
                return new InvocationResult<>(
                    HttpStatus.BAD_GATEWAY,
                    null,
                    new IllegalStateException("Received empty response body from listing query service"),
                    System.currentTimeMillis() - startedAt
                );
            }
            return new InvocationResult<>(
                HttpStatus.valueOf(response.getStatusCode().value()),
                response.getBody(),
                null,
                System.currentTimeMillis() - startedAt
            );
        } catch (HttpStatusCodeException exception) {
            return new InvocationResult<>(
                HttpStatus.valueOf(exception.getStatusCode().value()),
                null,
                exception,
                System.currentTimeMillis() - startedAt
            );
        } catch (RestClientException exception) {
            return new InvocationResult<>(
                HttpStatus.BAD_GATEWAY,
                null,
                exception,
                System.currentTimeMillis() - startedAt
            );
        }
    }

    private <T> InvocationResult<List<T>> invokeRemoteList(String path, Class<T[]> responseType) {
        long startedAt = System.currentTimeMillis();
        try {
            ResponseEntity<T[]> response = restTemplate.getForEntity(buildUri(path), responseType);
            T[] body = response.getBody();
            List<T> payload = body == null ? List.of() : Arrays.asList(body);
            return new InvocationResult<>(
                HttpStatus.valueOf(response.getStatusCode().value()),
                payload,
                null,
                System.currentTimeMillis() - startedAt
            );
        } catch (HttpStatusCodeException exception) {
            return new InvocationResult<>(
                HttpStatus.valueOf(exception.getStatusCode().value()),
                null,
                exception,
                System.currentTimeMillis() - startedAt
            );
        } catch (RestClientException exception) {
            return new InvocationResult<>(
                HttpStatus.BAD_GATEWAY,
                null,
                exception,
                System.currentTimeMillis() - startedAt
            );
        }
    }

    private boolean shouldAttemptRemote() {
        boolean configured = hasText(properties.getBaseUrl());
        if (!configured) {
            logger.warn("listing-query.proxy event=config-missing detail=baseUrl-empty");
        }
        return configured;
    }

    private URI buildUri(String path) {
        String baseUrl = properties.getBaseUrl().trim();
        String normalizedBaseUrl = baseUrl.endsWith("/")
            ? baseUrl.substring(0, baseUrl.length() - 1)
            : baseUrl;
        return URI.create(normalizedBaseUrl + path);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private ListingQueryServiceProperties.RolloutMode resolveRolloutMode() {
        ListingQueryServiceProperties.RolloutMode configuredMode = properties.getRolloutMode();
        if (configuredMode != null && configuredMode != ListingQueryServiceProperties.RolloutMode.DISABLED) {
            return configuredMode;
        }
        return properties.isEnabled()
            ? ListingQueryServiceProperties.RolloutMode.FULL
            : ListingQueryServiceProperties.RolloutMode.DISABLED;
    }

    private boolean shouldUseRemoteForCurrentRequest() {
        int rolloutPercent = Math.max(0, Math.min(properties.getRolloutPercent(), 100));
        if (rolloutPercent == 0) {
            return false;
        }
        if (rolloutPercent >= 100) {
            return true;
        }
        String key = currentRequestRoutingKey();
        int bucket = Math.floorMod(key.hashCode(), 100);
        return bucket < rolloutPercent;
    }

    private String currentRequestRoutingKey() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return "listing-query-default-routing-key";
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        String clientMarker = hasText(forwardedFor) ? forwardedFor : request.getRemoteAddr();
        String query = request.getQueryString();
        return request.getMethod()
            + "|"
            + request.getRequestURI()
            + "|"
            + (query == null ? "" : query)
            + "|"
            + clientMarker;
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }

    private String currentOperationPath(String defaultPath) {
        HttpServletRequest request = currentRequest();
        if (request == null || !hasText(request.getQueryString())) {
            return defaultPath;
        }
        return defaultPath + "?" + request.getQueryString();
    }

    private String currentRemotePath(String defaultPath) {
        return currentOperationPath(defaultPath);
    }

    private <T> T unwrap(InvocationResult<T> result, String remoteFailureMessage) {
        if (result.isSuccess()) {
            return result.body();
        }
        throw toResponseStatusException(result, remoteFailureMessage);
    }

    private <T> ResponseStatusException toResponseStatusException(
        InvocationResult<T> result,
        String remoteFailureMessage
    ) {
        if (result.exception() instanceof ResponseStatusException responseStatusException) {
            return responseStatusException;
        }

        HttpStatus status = result.status() == null ? HttpStatus.SERVICE_UNAVAILABLE : result.status();
        String message = status == HttpStatus.NOT_FOUND
            ? "Listing not found"
            : remoteFailureMessage;
        return new ResponseStatusException(status, message, result.exception());
    }

    private <T> void logShadowResult(
        String operation,
        InvocationResult<T> localResult,
        InvocationResult<T> remoteResult
    ) {
        logger.info(
            "listing-query.proxy event=shadow-compare operation={} localStatus={} remoteStatus={} localDurationMs={} remoteDurationMs={}",
            operation,
            localResult.status(),
            remoteResult.status(),
            localResult.durationMs(),
            remoteResult.durationMs()
        );

        if (!Objects.equals(localResult.status(), remoteResult.status())) {
            logger.warn(
                "listing-query.proxy event=status-mismatch rolloutMode=SHADOW operation={} localStatus={} remoteStatus={} localDurationMs={} remoteDurationMs={}",
                operation,
                localResult.status(),
                remoteResult.status(),
                localResult.durationMs(),
                remoteResult.durationMs()
            );
            return;
        }

        if (remoteResult.isSuccess() && !Objects.equals(localResult.body(), remoteResult.body())) {
            logger.warn(
                "listing-query.proxy event=payload-mismatch rolloutMode=SHADOW operation={} status={} localDurationMs={} remoteDurationMs={}",
                operation,
                localResult.status(),
                localResult.durationMs(),
                remoteResult.durationMs()
            );
        }
    }

    private void logProxyHit(
        String event,
        ListingQueryServiceProperties.RolloutMode rolloutMode,
        String operation
    ) {
        logger.info(
            "listing-query.proxy event={} rolloutMode={} operation={} failOpen={} baseUrl={}",
            event,
            rolloutMode,
            operation,
            properties.isFailOpen(),
            properties.getBaseUrl()
        );
    }

    private String fallbackReason(Exception exception) {
        return exception == null ? "unknown" : exception.getClass().getSimpleName();
    }

    private String fallbackMessage(Exception exception) {
        if (exception == null || exception.getMessage() == null || exception.getMessage().isBlank()) {
            return "n/a";
        }
        return exception.getMessage();
    }
}
