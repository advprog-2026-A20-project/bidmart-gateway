package id.ac.ui.cs.advprog.backend.controller;

import id.ac.ui.cs.advprog.backend.config.BiddingCommandServiceProperties;
import id.ac.ui.cs.advprog.backend.service.AuctionReadGateway;
import id.ac.ui.cs.advprog.backend.service.HttpProxyService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuctionCommandProxyControllerTest {

    private HttpProxyService proxyService;
    private AuctionReadGateway readGateway;
    private AuctionController controller;

    @BeforeEach
    void setUp() {
        proxyService = Mockito.mock(HttpProxyService.class);
        readGateway = Mockito.mock(AuctionReadGateway.class);
        when(readGateway.listAuctions()).thenReturn(List.of());
        BiddingCommandServiceProperties properties = new BiddingCommandServiceProperties();
        properties.setBaseUrl("http://bidding-command-service:8084");
        controller = new AuctionController(readGateway, proxyService, properties);
    }

    @Test
    void createAuctionForwardsToBiddingCommandService() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String body = "{\"listingId\":\"11111111-1111-1111-1111-111111111111\"}";

        when(proxyService.forward(
            HttpMethod.POST,
            "http://bidding-command-service:8084",
            "/api/auctions",
            request,
            body
        )).thenReturn(ResponseEntity.status(HttpStatus.CREATED).body("ok"));

        ResponseEntity<String> response = controller.createAuction(body, request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(proxyService).forward(
            HttpMethod.POST,
            "http://bidding-command-service:8084",
            "/api/auctions",
            request,
            body
        );
    }

    @Test
    void activateAuctionForwardsToBiddingCommandService() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        UUID auctionId = UUID.randomUUID();

        when(proxyService.forward(
            HttpMethod.POST,
            "http://bidding-command-service:8084",
            "/api/auctions/" + auctionId + "/activate",
            request,
            null
        )).thenReturn(ResponseEntity.ok("ok"));

        ResponseEntity<String> response = controller.activateAuction(auctionId, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(proxyService).forward(
            HttpMethod.POST,
            "http://bidding-command-service:8084",
            "/api/auctions/" + auctionId + "/activate",
            request,
            null
        );
    }

    @Test
    void placeBidForwardsToBiddingCommandService() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        UUID auctionId = UUID.randomUUID();
        String body = "{\"amount\":12500}";

        when(proxyService.forward(
            HttpMethod.POST,
            "http://bidding-command-service:8084",
            "/api/auctions/" + auctionId + "/bids",
            request,
            body
        )).thenReturn(ResponseEntity.status(HttpStatus.CREATED).body("ok"));

        ResponseEntity<String> response = controller.placeBid(auctionId, body, request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(proxyService).forward(
            HttpMethod.POST,
            "http://bidding-command-service:8084",
            "/api/auctions/" + auctionId + "/bids",
            request,
            body
        );
    }

    @Test
    void closeAuctionForwardsToBiddingCommandService() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        UUID auctionId = UUID.randomUUID();

        when(proxyService.forward(
            HttpMethod.POST,
            "http://bidding-command-service:8084",
            "/api/auctions/" + auctionId + "/close",
            request,
            null
        )).thenReturn(ResponseEntity.ok("ok"));

        ResponseEntity<String> response = controller.closeAuction(auctionId, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(proxyService).forward(
            HttpMethod.POST,
            "http://bidding-command-service:8084",
            "/api/auctions/" + auctionId + "/close",
            request,
            null
        );
    }

    @Test
    void getAuctionEndpointsUseReadGatewayInsteadOfCommandProxy() {
        UUID auctionId = UUID.randomUUID();

        controller.listAuctions();
        controller.getAuctionDetail(auctionId);
        controller.getBidHistory(auctionId);

        verify(readGateway).listAuctions();
        verify(readGateway).getAuctionDetail(auctionId);
        verify(readGateway).getBidHistory(auctionId);
        verify(proxyService, never()).forward(
            Mockito.any(),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.any(),
            Mockito.any()
        );
    }
}
