package id.ac.ui.cs.advprog.backend.service;

import id.ac.ui.cs.advprog.backend.config.AuctionQueryServiceProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProxyingAuctionReadGatewayTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void listAuctionsShouldForwardCurrentQueryString() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        AuctionQueryServiceProperties properties = new AuctionQueryServiceProperties();
        properties.setBaseUrl("http://auction-query");
        ProxyingAuctionReadGateway gateway = new ProxyingAuctionReadGateway(restTemplate, properties);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auctions");
        request.setQueryString("status=CLOSED");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        server.expect(requestTo("http://auction-query/api/auctions?status=CLOSED"))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        gateway.listAuctions();

        server.verify();
    }
}
