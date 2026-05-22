package id.ac.ui.cs.advprog.backend.service;

import id.ac.ui.cs.advprog.backend.config.ListingQueryServiceProperties;
import id.ac.ui.cs.advprog.backend.dto.ListingCategoryNodeResponse;
import id.ac.ui.cs.advprog.backend.dto.ListingDetailResponse;
import id.ac.ui.cs.advprog.backend.dto.ListingResponse;
import id.ac.ui.cs.advprog.backend.model.ListingCategory;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class ProxyingListingReadGatewayTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void listShouldUseCurrentRequestQueryAndMapArrayResponse() {
        RestTemplate restTemplate = new RestTemplate();
        ListingQueryServiceProperties properties = new ListingQueryServiceProperties();
        properties.setBaseUrl("http://listing-service/");
        ProxyingListingReadGateway gateway = new ProxyingListingReadGateway(restTemplate, properties);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setQueryString("category=ELECTRONICS&keyword=phone");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        UUID listingId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();

        server.expect(requestTo("http://listing-service/api/listings?category=ELECTRONICS&keyword=phone"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                    [{
                      "id":"%s",
                      "title":"Phone",
                      "description":"Smart phone",
                      "imageUrl":"https://img.example/phone.png",
                      "price":1200,
                      "category":"ELECTRONICS_SMARTPHONE",
                      "categoryPath":"Elektronik > Handphone > Smartphone",
                      "sellerId":"%s",
                      "sellerEmail":"seller@mail.com",
                      "status":"ACTIVE",
                      "totalBids":0,
                      "hasBids":false
                    }]
                    """.formatted(listingId, sellerId)));

        List<ListingResponse> responses = gateway.list(null, null, null, null, null, null);

        assertEquals(1, responses.size());
        assertEquals("Phone", responses.get(0).title());
        server.verify();
    }

    @Test
    void getByIdShouldReturnBadGatewayWhenBodyEmpty() {
        RestTemplate restTemplate = new RestTemplate();
        ListingQueryServiceProperties properties = new ListingQueryServiceProperties();
        properties.setBaseUrl("http://listing-service");
        ProxyingListingReadGateway gateway = new ProxyingListingReadGateway(restTemplate, properties);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        UUID listingId = UUID.randomUUID();
        server.expect(requestTo("http://listing-service/api/listings/" + listingId))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.OK));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> gateway.getById(listingId));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
        assertEquals("Listing service returned empty response", exception.getReason());
        server.verify();
    }

    @Test
    void categoriesAndCategoryTreeShouldDeserializeResponses() {
        RestTemplate restTemplate = new RestTemplate();
        ListingQueryServiceProperties properties = new ListingQueryServiceProperties();
        properties.setBaseUrl("http://listing-service");
        ProxyingListingReadGateway gateway = new ProxyingListingReadGateway(restTemplate, properties);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        server.expect(requestTo("http://listing-service/api/listings/categories"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_JSON)
                .body("[\"ELECTRONICS\",\"BOOKS\"]"));

        server.expect(requestTo("http://listing-service/api/listings/categories/tree"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                    [{
                      "key":"ELECTRONICS",
                      "label":"Elektronik",
                      "path":"Elektronik",
                      "children":[{"key":"ELECTRONICS_PHONE","label":"Handphone","path":"Elektronik > Handphone","children":[]}]
                    }]
                    """));

        List<ListingCategory> categories = gateway.categories();
        List<ListingCategoryNodeResponse> tree = gateway.categoryTree();

        assertEquals(List.of(ListingCategory.ELECTRONICS, ListingCategory.BOOKS), categories);
        assertEquals(ListingCategory.ELECTRONICS, tree.get(0).key());
        assertEquals(ListingCategory.ELECTRONICS_PHONE, tree.get(0).children().get(0).key());
        server.verify();
    }

    @Test
    void listShouldMapHttpErrorStatus() {
        RestTemplate restTemplate = new RestTemplate();
        ListingQueryServiceProperties properties = new ListingQueryServiceProperties();
        properties.setBaseUrl("http://listing-service");
        ProxyingListingReadGateway gateway = new ProxyingListingReadGateway(restTemplate, properties);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        server.expect(requestTo("http://listing-service/api/listings"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body("{}"));

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> gateway.list(null, null, new BigDecimal("200"), new BigDecimal("100"), null, null)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        server.verify();
    }

    @Test
    void missingBaseUrlShouldThrowServiceUnavailable() {
        RestTemplate restTemplate = new RestTemplate();
        ListingQueryServiceProperties properties = new ListingQueryServiceProperties();
        ProxyingListingReadGateway gateway = new ProxyingListingReadGateway(restTemplate, properties);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, gateway::categories);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
        assertEquals("Listing service base URL is not configured", exception.getReason());
    }
}
