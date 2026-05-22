package id.ac.ui.cs.advprog.backend.service;

import id.ac.ui.cs.advprog.backend.dto.AuctionCreateRequest;
import id.ac.ui.cs.advprog.backend.dto.AuctionDetailResponse;
import id.ac.ui.cs.advprog.backend.dto.BidPlaceRequest;
import id.ac.ui.cs.advprog.backend.model.AuctionStatus;
import id.ac.ui.cs.advprog.backend.model.ListingCategory;
import java.math.BigDecimal;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class BiddingCommandServiceClientTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void createAuctionShouldForwardAuthorizationHeader() {
        BiddingCommandServiceClient client = new BiddingCommandServiceClient(true, "http://bidding-service/");
        MockRestServiceServer server = serverFor(client);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token-123");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        UUID auctionId = UUID.randomUUID();
        server.expect(requestTo("http://bidding-service/api/auctions"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-123"))
            .andRespond(withStatus(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"id\":\"" + auctionId + "\",\"status\":\"ACTIVE\",\"totalBids\":0,\"reserveMet\":false,\"biddable\":true}"));

        AuctionCreateRequest payload = new AuctionCreateRequest(
            "Item",
            "Description",
            "https://img.example/item.png",
            ListingCategory.ELECTRONICS,
            new BigDecimal("100"),
            new BigDecimal("100"),
            new BigDecimal("10"),
            60L,
            true
        );

        AuctionDetailResponse response = client.createAuction(payload);

        assertNotNull(response);
        assertEquals(auctionId, response.id());
        assertEquals(AuctionStatus.ACTIVE, response.status());
        server.verify();
    }

    @Test
    void activateAuctionShouldThrowWhenClientDisabled() {
        BiddingCommandServiceClient client = new BiddingCommandServiceClient(false, "http://bidding-service");

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> client.activateAuction(UUID.randomUUID())
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
        assertEquals("Bidding command service base URL is not configured", exception.getReason());
    }

    @Test
    void placeBidShouldMapErrorMessage() {
        BiddingCommandServiceClient client = new BiddingCommandServiceClient(true, "http://bidding-service");
        MockRestServiceServer server = serverFor(client);

        UUID auctionId = UUID.randomUUID();
        server.expect(requestTo("http://bidding-service/api/auctions/" + auctionId + "/bids"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"message\":\"Bid amount too low\"}"));

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> client.placeBid(auctionId, new BidPlaceRequest(new BigDecimal("50")))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Bid amount too low", exception.getReason());
        server.verify();
    }

    @Test
    void closeAuctionShouldUseExpectedEndpoint() {
        BiddingCommandServiceClient client = new BiddingCommandServiceClient(true, "http://bidding-service");
        MockRestServiceServer server = serverFor(client);

        UUID auctionId = UUID.randomUUID();
        server.expect(requestTo("http://bidding-service/api/auctions/" + auctionId + "/close"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"id\":\"" + auctionId + "\",\"status\":\"CLOSED\",\"totalBids\":0,\"reserveMet\":false,\"biddable\":false}"));

        AuctionDetailResponse response = client.closeAuction(auctionId);

        assertNotNull(response);
        assertEquals(auctionId, response.id());
        assertEquals(AuctionStatus.CLOSED, response.status());
        server.verify();
    }

    private MockRestServiceServer serverFor(BiddingCommandServiceClient client) {
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        return MockRestServiceServer.bindTo(restTemplate).build();
    }
}
