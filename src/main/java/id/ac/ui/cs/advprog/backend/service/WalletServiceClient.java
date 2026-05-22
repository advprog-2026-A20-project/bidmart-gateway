package id.ac.ui.cs.advprog.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import id.ac.ui.cs.advprog.backend.dto.TopUpRequest;
import id.ac.ui.cs.advprog.backend.dto.TransactionResponse;
import id.ac.ui.cs.advprog.backend.dto.WalletResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Component
public class WalletServiceClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final boolean enabled;
    private final String baseUrl;

    public WalletServiceClient(
        @Qualifier("serviceCommandRestTemplate") RestTemplate restTemplate,
        @Value("${microservices.wallet.enabled:${WALLET_SERVICE_ENABLED:false}}") boolean enabled,
        @Value("${microservices.wallet.base-url:${WALLET_SERVICE_BASE_URL:}}") String baseUrl
    ) {
        this.restTemplate = restTemplate;
        this.enabled = enabled;
        this.baseUrl = trimTrailingSlash(baseUrl);
    }

    public boolean isEnabled() {
        return enabled && baseUrl != null && !baseUrl.isBlank();
    }

    public WalletResponse getBalance(UUID userId) {
        try {
            ensureConfigured();
            WalletBalanceServiceResponse response = restTemplate.exchange(
                baseUrl + "/wallets/" + userId + "/balance",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                WalletBalanceServiceResponse.class
            ).getBody();
            return toWalletResponse(response);
        } catch (HttpStatusCodeException exception) {
            throw toResponseStatusException(exception);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Wallet service is unavailable", exception);
        }
    }

    public WalletResponse topUp(UUID userId, TopUpRequest request) {
        try {
            ensureConfigured();
            WalletBalanceServiceResponse response = restTemplate.exchange(
                baseUrl + "/wallets/" + userId + "/top-up",
                HttpMethod.POST,
                new HttpEntity<>(request),
                WalletBalanceServiceResponse.class
            ).getBody();
            return toWalletResponse(response);
        } catch (HttpStatusCodeException exception) {
            throw toResponseStatusException(exception);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Wallet service is unavailable", exception);
        }
    }

    public List<TransactionResponse> getTransactions(UUID userId) {
        try {
            ensureConfigured();
            WalletTransactionServiceResponse[] response = restTemplate.exchange(
                baseUrl + "/wallets/" + userId + "/transactions",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                WalletTransactionServiceResponse[].class
            ).getBody();
            return response == null ? List.of() : Arrays.stream(response)
                .map(this::toTransactionResponse)
                .toList();
        } catch (HttpStatusCodeException exception) {
            throw toResponseStatusException(exception);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Wallet service is unavailable", exception);
        }
    }

    private void ensureConfigured() {
        if (!isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Wallet service base URL is not configured");
        }
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

    private WalletResponse toWalletResponse(WalletBalanceServiceResponse response) {
        if (response == null) {
            return null;
        }
        return new WalletResponse(
            response.userId(),
            response.availableBalance(),
            response.availableBalance(),
            response.heldBalance(),
            response.userId()
        );
    }

    private TransactionResponse toTransactionResponse(WalletTransactionServiceResponse response) {
        return new TransactionResponse(
            response.transactionId(),
            response.type(),
            response.amount(),
            response.availableBalanceAfter(),
            response.reference(),
            response.timestamp()
        );
    }

    private record WalletBalanceServiceResponse(
        UUID userId,
        BigDecimal availableBalance,
        BigDecimal heldBalance
    ) {
    }

    private record WalletTransactionServiceResponse(
        UUID transactionId,
        UUID userId,
        String type,
        BigDecimal amount,
        String reference,
        BigDecimal availableBalanceAfter,
        BigDecimal heldBalanceAfter,
        Instant timestamp
    ) {
    }
}
