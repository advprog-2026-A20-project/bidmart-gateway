package id.ac.ui.cs.advprog.backend.service;

import id.ac.ui.cs.advprog.backend.dto.AuctionDetailResponse;
import id.ac.ui.cs.advprog.backend.dto.BidPlaceRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class BiddingCommandServiceClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final boolean enabled;
    private final String baseUrl;

    public BiddingCommandServiceClient(
        @Value("${microservices.bidding-command.enabled:${BIDDING_COMMAND_SERVICE_ENABLED:false}}") boolean enabled,
        @Value("${microservices.bidding-command.base-url:${BIDDING_COMMAND_SERVICE_BASE_URL:}}") String baseUrl
    ) {
        this.enabled = enabled;
        this.baseUrl = trimTrailingSlash(baseUrl);
    }

    public boolean isEnabled() {
        return enabled && baseUrl != null && !baseUrl.isBlank();
    }

    public AuctionDetailResponse placeBid(UUID auctionId, BidPlaceRequest request) {
        return restTemplate.exchange(
            baseUrl + "/api/auctions/" + auctionId + "/bids",
            HttpMethod.POST,
            new HttpEntity<>(request, headersWithAuthorization()),
            AuctionDetailResponse.class
        ).getBody();
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

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
