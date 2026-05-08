package id.ac.ui.cs.advprog.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import id.ac.ui.cs.advprog.backend.config.AuctionQueryServiceProperties;
import id.ac.ui.cs.advprog.backend.dto.AuctionSummaryResponse;
import id.ac.ui.cs.advprog.backend.model.AuctionStatus;
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

class ProxyingAuctionReadGatewayTest {

    private final AuctionService auctionService = Mockito.mock(AuctionService.class);
    private final AuctionQueryServiceProperties properties = new AuctionQueryServiceProperties();
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private MockRestServiceServer server;
    private ProxyingAuctionReadGateway gateway;

    @BeforeEach
    void setUp() {
        server = MockRestServiceServer.createServer(restTemplate);
        gateway = new ProxyingAuctionReadGateway(auctionService, restTemplate, properties);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void listAuctionsShouldUseLocalServiceWhenProxyDisabled() {
        List<AuctionSummaryResponse> expected = List.of(sampleAuctionSummary());
        when(auctionService.listAuctions()).thenReturn(expected);

        List<AuctionSummaryResponse> response = gateway.listAuctions();

        assertEquals(expected, response);
        verify(auctionService).listAuctions();
        server.verify();
    }

    @Test
    void listAuctionsShouldUseRemoteServiceWhenProxyEnabled() throws Exception {
        properties.setEnabled(true);
        properties.setBaseUrl("http://auction-query");

        AuctionSummaryResponse summary = sampleAuctionSummary();
        server.expect(requestTo("http://auction-query/api/auctions"))
            .andRespond(withSuccess(
                objectMapper.writeValueAsString(List.of(summary)),
                MediaType.APPLICATION_JSON
            ));

        List<AuctionSummaryResponse> response = gateway.listAuctions();

        assertEquals(List.of(summary), response);
        server.verify();
    }

    @Test
    void listAuctionsShouldFallBackToLocalServiceWhenRemoteFailsAndFailOpenIsEnabled() {
        properties.setEnabled(true);
        properties.setBaseUrl("http://auction-query");
        properties.setFailOpen(true);

        List<AuctionSummaryResponse> fallback = List.of(sampleAuctionSummary());
        when(auctionService.listAuctions()).thenReturn(fallback);

        server.expect(requestTo("http://auction-query/api/auctions"))
            .andRespond(withServerError());

        List<AuctionSummaryResponse> response = gateway.listAuctions();

        assertEquals(fallback, response);
        verify(auctionService).listAuctions();
        server.verify();
    }

    @Test
    void listAuctionsShouldFallBackToLocalServiceWhenRemoteTimesOut() {
        properties.setEnabled(true);
        properties.setBaseUrl("http://auction-query");
        properties.setFailOpen(true);

        List<AuctionSummaryResponse> fallback = List.of(sampleAuctionSummary());
        when(auctionService.listAuctions()).thenReturn(fallback);

        server.expect(requestTo("http://auction-query/api/auctions"))
            .andRespond(withException(new SocketTimeoutException("Read timed out")));

        List<AuctionSummaryResponse> response = gateway.listAuctions();

        assertEquals(fallback, response);
        verify(auctionService).listAuctions();
        server.verify();
    }

    @Test
    void listAuctionsShouldFallBackToLocalServiceWhenRemoteIsUnavailable() {
        properties.setEnabled(true);
        properties.setBaseUrl("http://auction-query");
        properties.setFailOpen(true);

        List<AuctionSummaryResponse> fallback = List.of(sampleAuctionSummary());
        when(auctionService.listAuctions()).thenReturn(fallback);

        server.expect(requestTo("http://auction-query/api/auctions"))
            .andRespond(withException(new ConnectException("Connection refused")));

        List<AuctionSummaryResponse> response = gateway.listAuctions();

        assertEquals(fallback, response);
        verify(auctionService).listAuctions();
        server.verify();
    }

    @Test
    void listAuctionsShouldReturnLocalResponseInShadowMode() throws Exception {
        properties.setRolloutMode(AuctionQueryServiceProperties.RolloutMode.SHADOW);
        properties.setBaseUrl("http://auction-query");

        List<AuctionSummaryResponse> localResponse = List.of(sampleAuctionSummary());
        when(auctionService.listAuctions()).thenReturn(localResponse);

        AuctionSummaryResponse remoteSummary = new AuctionSummaryResponse(
            UUID.fromString("aaaaaaaa-1111-1111-1111-111111111111"),
            UUID.fromString("bbbbbbbb-2222-2222-2222-222222222222"),
            "Remote Listing",
            "Remote description",
            UUID.fromString("cccccccc-3333-3333-3333-333333333333"),
            "remote@example.com",
            new BigDecimal("2000.00"),
            new BigDecimal("1500.00"),
            new BigDecimal("10.00"),
            AuctionStatus.ACTIVE,
            Instant.parse("2026-04-24T10:00:00Z"),
            Instant.parse("2026-04-24T10:00:00Z"),
            Instant.parse("2026-04-24T11:00:00Z"),
            0,
            1L,
            new BigDecimal("2010.00")
        );

        server.expect(requestTo("http://auction-query/api/auctions"))
            .andRespond(withSuccess(
                objectMapper.writeValueAsString(List.of(remoteSummary)),
                MediaType.APPLICATION_JSON
            ));

        List<AuctionSummaryResponse> response = gateway.listAuctions();

        assertEquals(localResponse, response);
        verify(auctionService).listAuctions();
        server.verify();
    }

    @Test
    void listAuctionsShouldUseRemoteServiceWhenPercentRolloutIsHundred() throws Exception {
        properties.setRolloutMode(AuctionQueryServiceProperties.RolloutMode.PERCENT);
        properties.setRolloutPercent(100);
        properties.setBaseUrl("http://auction-query");

        AuctionSummaryResponse summary = sampleAuctionSummary();
        server.expect(requestTo("http://auction-query/api/auctions"))
            .andRespond(withSuccess(
                objectMapper.writeValueAsString(List.of(summary)),
                MediaType.APPLICATION_JSON
            ));

        List<AuctionSummaryResponse> response = gateway.listAuctions();

        assertEquals(List.of(summary), response);
        server.verify();
    }

    private AuctionSummaryResponse sampleAuctionSummary() {
        return new AuctionSummaryResponse(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            UUID.fromString("22222222-2222-2222-2222-222222222222"),
            "Gaming Phone",
            "Competitive smartphone",
            UUID.fromString("33333333-3333-3333-3333-333333333333"),
            "seller@example.com",
            new BigDecimal("1250.00"),
            new BigDecimal("1200.00"),
            new BigDecimal("10.00"),
            AuctionStatus.ACTIVE,
            Instant.parse("2026-04-24T10:00:00Z"),
            Instant.parse("2026-04-24T10:00:00Z"),
            Instant.parse("2026-04-24T11:00:00Z"),
            0,
            1L,
            new BigDecimal("1260.00")
        );
    }
}
