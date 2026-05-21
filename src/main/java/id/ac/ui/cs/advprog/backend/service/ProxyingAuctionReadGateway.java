package id.ac.ui.cs.advprog.backend.service;

import id.ac.ui.cs.advprog.backend.config.AuctionQueryServiceProperties;
import id.ac.ui.cs.advprog.backend.dto.AuctionDetailResponse;
import id.ac.ui.cs.advprog.backend.dto.AuctionSummaryResponse;
import id.ac.ui.cs.advprog.backend.dto.BidResponse;
import java.net.URI;
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
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProxyingAuctionReadGateway implements AuctionReadGateway {

    private final RestTemplate restTemplate;
    private final AuctionQueryServiceProperties properties;

    public ProxyingAuctionReadGateway(
        @Qualifier("auctionQueryRestTemplate") RestTemplate restTemplate,
        AuctionQueryServiceProperties properties
    ) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    @Override
    public List<AuctionSummaryResponse> listAuctions() {
        return getList("/api/auctions", AuctionSummaryResponse[].class);
    }

    @Override
    public AuctionDetailResponse getAuctionDetail(UUID auctionId) {
        return getObject("/api/auctions/" + auctionId, AuctionDetailResponse.class);
    }

    @Override
    public List<BidResponse> getBidHistory(UUID auctionId) {
        return getList("/api/auctions/" + auctionId + "/bids", BidResponse[].class);
    }

    private <T> T getObject(String path, Class<T> responseType) {
        try {
            ResponseEntity<T> response = restTemplate.getForEntity(buildUri(path), responseType);
            T body = response.getBody();
            if (body == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Auction query service returned empty response");
            }
            return body;
        } catch (HttpStatusCodeException exception) {
            throw new ResponseStatusException(exception.getStatusCode(), exception.getStatusText(), exception);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Auction query service is unavailable", exception);
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
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Auction query service is unavailable", exception);
        }
    }

    private URI buildUri(String path) {
        String baseUrl = properties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Auction query service base URL is not configured");
        }
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return URI.create(normalizedBaseUrl + path);
    }
}
