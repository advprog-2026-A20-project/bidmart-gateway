package id.ac.ui.cs.advprog.backend.controller;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuctionCommandProxyControllerTest {

    private HttpProxyService proxyService;
    private AuctionController controller;

    @BeforeEach
    void setUp() {
        proxyService = Mockito.mock(HttpProxyService.class);
        AuctionReadGateway readGateway = Mockito.mock(AuctionReadGateway.class);
        when(readGateway.listAuctions()).thenReturn(List.of());
        controller = new AuctionController(readGateway, proxyService, "http://bidding-command-service:8084");
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
}
