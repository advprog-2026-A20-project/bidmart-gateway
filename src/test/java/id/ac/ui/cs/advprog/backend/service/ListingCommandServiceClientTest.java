package id.ac.ui.cs.advprog.backend.service;

import id.ac.ui.cs.advprog.backend.config.ListingQueryServiceProperties;
import id.ac.ui.cs.advprog.backend.dto.ListingBidValidationResponse;
import id.ac.ui.cs.advprog.backend.dto.ListingCreateRequest;
import id.ac.ui.cs.advprog.backend.dto.ListingDetailResponse;
import id.ac.ui.cs.advprog.backend.dto.ListingResponse;
import id.ac.ui.cs.advprog.backend.dto.ListingUpdateRequest;
import id.ac.ui.cs.advprog.backend.dto.PublicSellerProfileResponse;
import id.ac.ui.cs.advprog.backend.model.AuctionStatus;
import id.ac.ui.cs.advprog.backend.model.ListingCategory;
import id.ac.ui.cs.advprog.backend.model.ListingStatus;
import id.ac.ui.cs.advprog.backend.model.Role;
import java.math.BigDecimal;
import java.time.Instant;
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

class ListingCommandServiceClientTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void createShouldForwardAuthorizationHeaderAndReturnResponse() {
        ListingQueryServiceProperties properties = new ListingQueryServiceProperties();
        properties.setBaseUrl("http://listing-service/");
        ListingCommandServiceClient client = new ListingCommandServiceClient(properties);
        MockRestServiceServer server = serverFor(client);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer listing-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        UUID listingId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();

        server.expect(requestTo("http://listing-service/api/listings"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer listing-token"))
            .andRespond(withStatus(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                    {
                      "id":"%s",
                      "title":"Item",
                      "description":"Description",
                      "imageUrl":"https://img.example/item.png",
                      "price":100,
                      "category":"ELECTRONICS",
                      "categoryPath":"Elektronik",
                      "sellerId":"%s",
                      "sellerEmail":"seller@mail.com",
                      "status":"ACTIVE",
                      "totalBids":0,
                      "hasBids":false
                    }
                    """.formatted(listingId, sellerId)));

        ListingResponse response = client.create(new ListingCreateRequest(
            "Item",
            "Description",
            "https://img.example/item.png",
            new BigDecimal("100"),
            ListingCategory.ELECTRONICS
        ));

        assertNotNull(response);
        assertEquals(listingId, response.id());
        assertEquals(sellerId, response.sellerId());
        server.verify();
    }

    @Test
    void updateShouldMapErrorMessageFromBody() {
        ListingQueryServiceProperties properties = new ListingQueryServiceProperties();
        properties.setBaseUrl("http://listing-service");
        ListingCommandServiceClient client = new ListingCommandServiceClient(properties);
        MockRestServiceServer server = serverFor(client);

        UUID listingId = UUID.randomUUID();
        server.expect(requestTo("http://listing-service/api/listings/" + listingId))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"message\":\"Listing already has bids\"}"));

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> client.update(listingId, new ListingUpdateRequest("New desc", "https://img/new.png", ListingCategory.BOOKS))
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("Listing already has bids", exception.getReason());
        server.verify();
    }

    @Test
    void cancelShouldReturnDetailResponse() {
        ListingQueryServiceProperties properties = new ListingQueryServiceProperties();
        properties.setBaseUrl("http://listing-service");
        ListingCommandServiceClient client = new ListingCommandServiceClient(properties);
        MockRestServiceServer server = serverFor(client);

        UUID listingId = UUID.randomUUID();

        server.expect(requestTo("http://listing-service/api/listings/" + listingId))
            .andExpect(method(HttpMethod.DELETE))
            .andRespond(withStatus(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                    {
                      "id":"%s",
                      "title":"Item",
                      "description":"Description",
                      "imageUrl":"https://img.example/item.png",
                      "price":100,
                      "startingPrice":100,
                      "reservePrice":100,
                      "minimumBidIncrement":10,
                      "durationMinutes":60,
                      "category":"ELECTRONICS",
                      "categoryPath":"Elektronik",
                      "sellerId":"%s",
                      "sellerEmail":"seller@mail.com",
                      "status":"CANCELLED",
                      "auctionStatus":"CLOSED",
                      "totalBids":0,
                      "hasBids":false,
                      "cancelledAt":"2026-01-01T00:00:00Z"
                    }
                    """.formatted(listingId, UUID.randomUUID())));

        ListingDetailResponse response = client.cancel(listingId);

        assertNotNull(response);
        assertEquals(ListingStatus.CANCELLED, response.status());
        assertEquals(AuctionStatus.CLOSED, response.auctionStatus());
        server.verify();
    }

    @Test
    void validateForBidShouldReturnGatewayUnavailableWhenServiceDown() {
        ListingQueryServiceProperties properties = new ListingQueryServiceProperties();
        properties.setBaseUrl("http://127.0.0.1:1");
        ListingCommandServiceClient client = new ListingCommandServiceClient(properties);

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> client.validateForBid(UUID.randomUUID())
        );

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
        assertEquals("Listing service is unavailable", exception.getReason());
    }

    @Test
    void publicProfileShouldDeserializeResponse() {
        ListingQueryServiceProperties properties = new ListingQueryServiceProperties();
        properties.setBaseUrl("http://listing-service");
        ListingCommandServiceClient client = new ListingCommandServiceClient(properties);
        MockRestServiceServer server = serverFor(client);

        UUID userId = UUID.randomUUID();

        server.expect(requestTo("http://listing-service/api/users/" + userId + "/public-profile"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                    {
                      "id":"%s",
                      "email":"seller@mail.com",
                      "role":"SELLER",
                      "activeListingCount":2,
                      "liveAuctionCount":1,
                      "completedAuctionCount":1
                    }
                    """.formatted(userId)));

        PublicSellerProfileResponse response = client.publicProfile(userId);

        assertEquals(userId, response.id());
        assertEquals(Role.SELLER, response.role());
        assertEquals(2, response.activeListingCount());
        server.verify();
    }

    @Test
    void missingBaseUrlShouldThrowServiceUnavailable() {
        ListingQueryServiceProperties properties = new ListingQueryServiceProperties();
        ListingCommandServiceClient client = new ListingCommandServiceClient(properties);

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> client.create(new ListingCreateRequest(
                "Item",
                "Description",
                "https://img.example/item.png",
                new BigDecimal("100"),
                ListingCategory.ELECTRONICS
            ))
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
        assertEquals("Listing service base URL is not configured", exception.getReason());
    }

    @Test
    void validateForBidShouldReturnValidationResponse() {
        ListingQueryServiceProperties properties = new ListingQueryServiceProperties();
        properties.setBaseUrl("http://listing-service");
        ListingCommandServiceClient client = new ListingCommandServiceClient(properties);
        MockRestServiceServer server = serverFor(client);

        UUID listingId = UUID.randomUUID();
        server.expect(requestTo("http://listing-service/api/listings/" + listingId + "/validation"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                    {
                      "listingId":"%s",
                      "active":true,
                      "biddable":true,
                      "message":"ok",
                      "listingStatus":"ACTIVE",
                      "auctionStatus":"ACTIVE",
                      "endsAt":"2026-01-01T00:00:00Z"
                    }
                    """.formatted(listingId)));

        ListingBidValidationResponse response = client.validateForBid(listingId);

        assertEquals(listingId, response.listingId());
        assertEquals(true, response.biddable());
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), response.endsAt());
        server.verify();
    }

    private MockRestServiceServer serverFor(ListingCommandServiceClient client) {
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        return MockRestServiceServer.bindTo(restTemplate).build();
    }
}
