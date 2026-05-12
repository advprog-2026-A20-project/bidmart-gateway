package id.ac.ui.cs.advprog.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import id.ac.ui.cs.advprog.backend.config.ListingQueryServiceProperties;
import id.ac.ui.cs.advprog.backend.dto.ListingCategoryNodeResponse;
import id.ac.ui.cs.advprog.backend.dto.ListingResponse;
import id.ac.ui.cs.advprog.backend.model.AuctionStatus;
import id.ac.ui.cs.advprog.backend.model.ListingCategory;
import id.ac.ui.cs.advprog.backend.model.ListingStatus;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProxyingListingReadGatewayTest {

    private final ListingService listingService = Mockito.mock(ListingService.class);
    private final ListingQueryServiceProperties properties = new ListingQueryServiceProperties();
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private MockRestServiceServer server;
    private ProxyingListingReadGateway gateway;

    @BeforeEach
    void setUp() {
        server = MockRestServiceServer.createServer(restTemplate);
        gateway = new ProxyingListingReadGateway(listingService, restTemplate, properties);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void listShouldUseLocalServiceWhenProxyDisabled() {
        List<ListingResponse> expected = List.of(sampleListingResponse());
        when(listingService.getAllListings(Pageable.unpaged(), null, null, null, null, null, null))
            .thenReturn(expected);

        List<ListingResponse> response = gateway.list(Pageable.unpaged(), null, null, null, null, null, null);

        assertEquals(expected, response);
        verify(listingService).getAllListings(Pageable.unpaged(), null, null, null, null, null, null);
        server.verify();
    }

    @Test
    void listShouldUseRemoteServiceWhenProxyEnabled() throws Exception {
        properties.setEnabled(true);
        properties.setBaseUrl("http://listing-query");

        ListingResponse listing = sampleListingResponse();
        server.expect(requestTo("http://listing-query/api/listings"))
            .andRespond(withSuccess(
                objectMapper.writeValueAsString(List.of(listing)),
                MediaType.APPLICATION_JSON
            ));

        List<ListingResponse> response = gateway.list(Pageable.unpaged(), null, null, null, null, null, null);

        assertEquals(List.of(listing), response);
        server.verify();
    }

    @Test
    void listShouldFallBackToLocalServiceWhenRemoteFailsAndFailOpenIsEnabled() {
        properties.setEnabled(true);
        properties.setBaseUrl("http://listing-query");
        properties.setFailOpen(true);

        List<ListingResponse> fallback = List.of(sampleListingResponse());
        when(listingService.getAllListings(Pageable.unpaged(), null, null, null, null, null, null))
            .thenReturn(fallback);

        server.expect(requestTo("http://listing-query/api/listings"))
            .andRespond(withServerError());

        List<ListingResponse> response = gateway.list(Pageable.unpaged(), null, null, null, null, null, null);

        assertEquals(fallback, response);
        verify(listingService).getAllListings(Pageable.unpaged(), null, null, null, null, null, null);
        server.verify();
    }

    @Test
    void listShouldFallBackToLocalServiceWhenRemoteTimesOut() {
        properties.setEnabled(true);
        properties.setBaseUrl("http://listing-query");
        properties.setFailOpen(true);

        List<ListingResponse> fallback = List.of(sampleListingResponse());
        when(listingService.getAllListings(Pageable.unpaged(), null, null, null, null, null, null))
            .thenReturn(fallback);

        server.expect(requestTo("http://listing-query/api/listings"))
            .andRespond(withException(new SocketTimeoutException("Read timed out")));

        List<ListingResponse> response = gateway.list(Pageable.unpaged(), null, null, null, null, null, null);

        assertEquals(fallback, response);
        server.verify();
    }

    @Test
    void listShouldFallBackToLocalServiceWhenRemoteIsUnavailable() {
        properties.setEnabled(true);
        properties.setBaseUrl("http://listing-query");
        properties.setFailOpen(true);

        List<ListingResponse> fallback = List.of(sampleListingResponse());
        when(listingService.getAllListings(Pageable.unpaged(), null, null, null, null, null, null))
            .thenReturn(fallback);

        server.expect(requestTo("http://listing-query/api/listings"))
            .andRespond(withException(new ConnectException("Connection refused")));

        List<ListingResponse> response = gateway.list(Pageable.unpaged(), null, null, null, null, null, null);

        assertEquals(fallback, response);
        server.verify();
    }

    @Test
    void categoryTreeShouldUseRemoteServiceWhenPercentRolloutIsHundred() throws Exception {
        properties.setRolloutMode(ListingQueryServiceProperties.RolloutMode.PERCENT);
        properties.setRolloutPercent(100);
        properties.setBaseUrl("http://listing-query");

        ListingCategoryNodeResponse node = new ListingCategoryNodeResponse(
            ListingCategory.ELECTRONICS,
            "Elektronik",
            "Elektronik",
            List.of()
        );

        server.expect(requestTo("http://listing-query/api/listings/categories/tree"))
            .andRespond(withSuccess(
                objectMapper.writeValueAsString(List.of(node)),
                MediaType.APPLICATION_JSON
            ));

        List<ListingCategoryNodeResponse> response = gateway.categoryTree();

        assertEquals(List.of(node), response);
        server.verify();
    }

    private ListingResponse sampleListingResponse() {
        return new ListingResponse(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "Gaming Phone",
            "Competitive smartphone",
            "https://img.example/phone.jpg",
            new BigDecimal("1250.00"),
            ListingCategory.ELECTRONICS_SMARTPHONE,
            "Elektronik > Handphone > Smartphone",
            UUID.fromString("33333333-3333-3333-3333-333333333333"),
            "seller@example.com",
            ListingStatus.ACTIVE,
            UUID.fromString("44444444-4444-4444-4444-444444444444"),
            AuctionStatus.ACTIVE,
            Instant.parse("2026-04-24T11:00:00Z"),
            1L,
            true,
            Instant.parse("2026-04-24T10:00:00Z"),
            null,
            null
        );
    }
}
