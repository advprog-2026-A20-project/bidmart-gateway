package id.ac.ui.cs.advprog.backend.controller;

import id.ac.ui.cs.advprog.backend.dto.AuctionDetailResponse;
import id.ac.ui.cs.advprog.backend.dto.AuctionSummaryResponse;
import id.ac.ui.cs.advprog.backend.dto.BidResponse;
import id.ac.ui.cs.advprog.backend.service.AuctionReadGateway;
import id.ac.ui.cs.advprog.backend.service.HttpProxyService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auctions")
public class AuctionController {

    private final AuctionReadGateway auctionReadGateway;
    private final HttpProxyService proxyService;
    private final String biddingCommandServiceBaseUrl;

    public AuctionController(
        AuctionReadGateway auctionReadGateway,
        HttpProxyService proxyService,
        @Value("${BIDDING_COMMAND_SERVICE_BASE_URL:http://localhost:8084}") String biddingCommandServiceBaseUrl
    ) {
        this.auctionReadGateway = auctionReadGateway;
        this.proxyService = proxyService;
        this.biddingCommandServiceBaseUrl = biddingCommandServiceBaseUrl;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<String> createAuction(
        @RequestBody String body,
        HttpServletRequest httpServletRequest
    ) {
        return proxyService.forward(
            HttpMethod.POST,
            biddingCommandServiceBaseUrl,
            "/api/auctions",
            httpServletRequest,
            body
        );
    }

    @GetMapping
    @PreAuthorize("permitAll()")
    public List<AuctionSummaryResponse> listAuctions() {
        return auctionReadGateway.listAuctions();
    }

    @GetMapping("/{auctionId}")
    @PreAuthorize("permitAll()")
    public AuctionDetailResponse getAuctionDetail(@PathVariable UUID auctionId) {
        return auctionReadGateway.getAuctionDetail(auctionId);
    }

    @GetMapping("/{auctionId}/bids")
    @PreAuthorize("permitAll()")
    public List<BidResponse> getBidHistory(@PathVariable UUID auctionId) {
        return auctionReadGateway.getBidHistory(auctionId);
    }

    @PostMapping("/{auctionId}/activate")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<String> activateAuction(
        @PathVariable UUID auctionId,
        HttpServletRequest httpServletRequest
    ) {
        return proxyService.forward(
            HttpMethod.POST,
            biddingCommandServiceBaseUrl,
            "/api/auctions/" + auctionId + "/activate",
            httpServletRequest,
            null
        );
    }

    @PostMapping("/{auctionId}/bids")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<String> placeBid(
        @PathVariable UUID auctionId,
        @RequestBody String body,
        HttpServletRequest httpServletRequest
    ) {
        return proxyService.forward(
            HttpMethod.POST,
            biddingCommandServiceBaseUrl,
            "/api/auctions/" + auctionId + "/bids",
            httpServletRequest,
            body
        );
    }

    @PostMapping("/{auctionId}/close")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<String> closeAuction(
        @PathVariable UUID auctionId,
        HttpServletRequest httpServletRequest
    ) {
        return proxyService.forward(
            HttpMethod.POST,
            biddingCommandServiceBaseUrl,
            "/api/auctions/" + auctionId + "/close",
            httpServletRequest,
            null
        );
    }
}
