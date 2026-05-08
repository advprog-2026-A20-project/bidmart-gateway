package id.ac.ui.cs.advprog.backend.controller;

import id.ac.ui.cs.advprog.backend.dto.AuctionCreateRequest;
import id.ac.ui.cs.advprog.backend.dto.AuctionDetailResponse;
import id.ac.ui.cs.advprog.backend.dto.AuctionSummaryResponse;
import id.ac.ui.cs.advprog.backend.dto.BidPlaceRequest;
import id.ac.ui.cs.advprog.backend.dto.BidResponse;
import id.ac.ui.cs.advprog.backend.security.AuthenticatedUser;
import id.ac.ui.cs.advprog.backend.service.AuctionReadGateway;
import id.ac.ui.cs.advprog.backend.service.AuctionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    private final AuctionService auctionService;
    private final AuctionReadGateway auctionReadGateway;

    public AuctionController(AuctionService auctionService, AuctionReadGateway auctionReadGateway) {
        this.auctionService = auctionService;
        this.auctionReadGateway = auctionReadGateway;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SELLER')")
    public AuctionDetailResponse createAuction(
        @Valid @RequestBody AuctionCreateRequest request,
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return auctionService.createAuction(request, authenticatedUser.id());
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
    public AuctionDetailResponse activateAuction(
        @PathVariable UUID auctionId,
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return auctionService.activateAuction(auctionId, authenticatedUser.id());
    }

    @PostMapping("/{auctionId}/bids")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('BUYER')")
    public AuctionDetailResponse placeBid(
        @PathVariable UUID auctionId,
        @Valid @RequestBody BidPlaceRequest request,
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return auctionService.placeBid(auctionId, request, authenticatedUser.id());
    }

    @PostMapping("/{auctionId}/close")
    @PreAuthorize("hasRole('SELLER')")
    public AuctionDetailResponse closeAuction(
        @PathVariable UUID auctionId,
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return auctionService.closeAuction(auctionId, authenticatedUser.id());
    }
}
