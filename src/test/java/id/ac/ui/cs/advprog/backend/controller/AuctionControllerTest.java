package id.ac.ui.cs.advprog.backend.controller;

import id.ac.ui.cs.advprog.backend.dto.AuctionCreateRequest;
import id.ac.ui.cs.advprog.backend.dto.AuctionDetailResponse;
import id.ac.ui.cs.advprog.backend.dto.AuctionSummaryResponse;
import id.ac.ui.cs.advprog.backend.dto.BidPlaceRequest;
import id.ac.ui.cs.advprog.backend.dto.BidResponse;
import id.ac.ui.cs.advprog.backend.model.AuctionStatus;
import id.ac.ui.cs.advprog.backend.model.ListingCategory;
import id.ac.ui.cs.advprog.backend.model.Role;
import id.ac.ui.cs.advprog.backend.security.AuthenticatedUser;
import id.ac.ui.cs.advprog.backend.service.AuctionReadGateway;
import id.ac.ui.cs.advprog.backend.service.BiddingCommandServiceClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuctionControllerTest {

    private final AuctionReadGateway readGateway = mock(AuctionReadGateway.class);
    private final BiddingCommandServiceClient commandClient = mock(BiddingCommandServiceClient.class);
    private final AuctionController controller = new AuctionController(readGateway, commandClient);

    @Test
    void createActivateBidCloseShouldDelegateToCommandClient() {
        UUID auctionId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "seller@mail.com", Role.SELLER);
        AuctionCreateRequest createRequest = new AuctionCreateRequest(
            "Title", "Desc", "https://img/x.png", ListingCategory.ELECTRONICS,
            new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10"), 60L, true
        );
        BidPlaceRequest bidRequest = new BidPlaceRequest(new BigDecimal("120"));
        AuctionDetailResponse detail = new AuctionDetailResponse(
            auctionId, UUID.randomUUID(), "Title", "Desc", user.id(), user.email(),
            new BigDecimal("120"), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10"),
            AuctionStatus.ACTIVE, Instant.now(), Instant.now(), Instant.now(), null,
            60L, 0, 1, new BigDecimal("130"), true, true, null, null, List.of()
        );

        when(commandClient.createAuction(createRequest)).thenReturn(detail);
        when(commandClient.activateAuction(auctionId)).thenReturn(detail);
        when(commandClient.placeBid(auctionId, bidRequest)).thenReturn(detail);
        when(commandClient.closeAuction(auctionId)).thenReturn(detail);

        assertEquals(detail, controller.createAuction(createRequest, user));
        assertEquals(detail, controller.activateAuction(auctionId, user));
        assertEquals(detail, controller.placeBid(auctionId, bidRequest, user));
        assertEquals(detail, controller.closeAuction(auctionId, user));

        verify(commandClient).createAuction(createRequest);
        verify(commandClient).activateAuction(auctionId);
        verify(commandClient).placeBid(auctionId, bidRequest);
        verify(commandClient).closeAuction(auctionId);
    }

    @Test
    void readEndpointsShouldDelegateToReadGateway() {
        UUID auctionId = UUID.randomUUID();
        AuctionSummaryResponse summary = new AuctionSummaryResponse(
            auctionId,
            UUID.randomUUID(),
            "Title",
            "Desc",
            UUID.randomUUID(),
            "seller@mail.com",
            new BigDecimal("120"),
            new BigDecimal("100"),
            new BigDecimal("10"),
            AuctionStatus.ACTIVE,
            Instant.now(),
            Instant.now(),
            Instant.now(),
            0,
            0L,
            new BigDecimal("130")
        );
        BidResponse bid = new BidResponse(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "buyer@mail.com",
            new BigDecimal("110"),
            1L,
            Instant.now(),
            false
        );
        AuctionDetailResponse detail = new AuctionDetailResponse(
            auctionId, UUID.randomUUID(), "Title", "Desc", UUID.randomUUID(), "seller@mail.com",
            new BigDecimal("110"), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10"),
            AuctionStatus.ACTIVE, Instant.now(), Instant.now(), Instant.now(), null,
            60L, 0, 1, new BigDecimal("120"), true, true, bid, null, List.of(bid)
        );

        when(readGateway.listAuctions()).thenReturn(List.of(summary));
        when(readGateway.getAuctionDetail(auctionId)).thenReturn(detail);
        when(readGateway.getBidHistory(auctionId)).thenReturn(List.of(bid));

        assertEquals(1, controller.listAuctions().size());
        assertEquals(detail, controller.getAuctionDetail(auctionId));
        assertEquals(1, controller.getBidHistory(auctionId).size());

        verify(readGateway).listAuctions();
        verify(readGateway).getAuctionDetail(auctionId);
        verify(readGateway).getBidHistory(auctionId);
    }
}
