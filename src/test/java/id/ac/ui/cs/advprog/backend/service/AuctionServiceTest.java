package id.ac.ui.cs.advprog.backend.service;

import id.ac.ui.cs.advprog.backend.dto.AuctionCreateRequest;
import id.ac.ui.cs.advprog.backend.dto.AuctionDetailResponse;
import id.ac.ui.cs.advprog.backend.dto.AuctionSummaryResponse;
import id.ac.ui.cs.advprog.backend.dto.BidPlaceRequest;
import id.ac.ui.cs.advprog.backend.dto.BidResponse;
import id.ac.ui.cs.advprog.backend.model.Auction;
import id.ac.ui.cs.advprog.backend.model.AuctionStatus;
import id.ac.ui.cs.advprog.backend.model.Bid;
import id.ac.ui.cs.advprog.backend.model.Listing;
import id.ac.ui.cs.advprog.backend.model.ListingCategory;
import id.ac.ui.cs.advprog.backend.model.Role;
import id.ac.ui.cs.advprog.backend.model.User;
import id.ac.ui.cs.advprog.backend.repository.AuctionEventRepository;
import id.ac.ui.cs.advprog.backend.repository.AuctionRepository;
import id.ac.ui.cs.advprog.backend.repository.BidRepository;
import id.ac.ui.cs.advprog.backend.repository.ListingRepository;
import id.ac.ui.cs.advprog.backend.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "security.jwt.secret=test-secret-please-change-32-chars",
    "security.jwt.expiration-seconds=3600"
})
@ActiveProfiles("test")
@Import(AuctionServiceTest.ClockTestConfiguration.class)
class AuctionServiceTest {

    private static final Instant BASE_TIME = Instant.parse("2026-04-14T05:00:00Z");

    @Autowired
    private AuctionService auctionService;

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private BidRepository bidRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ListingRepository listingRepository;

    @Autowired
    private AuctionEventRepository auctionEventRepository;

    @Autowired
    private InMemoryListingPriceUpdateQueue listingPriceUpdateQueue;

    @Autowired
    private MutableClock mutableClock;

    private User seller;
    private User alternateSeller;
    private User buyer;
    private User competingBuyer;

    @BeforeEach
    void setUp() {
        listingPriceUpdateQueue.flushPendingUpdates();
        bidRepository.deleteAll();
        auctionRepository.deleteAll();
        auctionEventRepository.deleteAll();
        listingRepository.deleteAll();
        userRepository.deleteAll();

        mutableClock.setInstant(BASE_TIME);

        seller = saveUser(Role.SELLER, money("0.00"));
        alternateSeller = saveUser(Role.SELLER, money("0.00"));
        buyer = saveUser(Role.BUYER, money("1000.00"));
        competingBuyer = saveUser(Role.BUYER, money("1000.00"));
    }

    @Test
    void placeBidAcceptsValidBidAndUpdatesBalances() {
        AuctionDetailResponse createdAuction = auctionService.createAuction(
            auctionRequest(true, 30L, "100.00", "150.00", "10.00"),
            seller.getId()
        );

        AuctionDetailResponse updatedAuction = auctionService.placeBid(
            createdAuction.id(),
            new BidPlaceRequest(money("120.00")),
            buyer.getId()
        );

        User reloadedBuyer = userRepository.findById(buyer.getId()).orElseThrow();
        assertEquals(AuctionStatus.ACTIVE, updatedAuction.status());
        assertEquals(money("120.00"), updatedAuction.leadingBid().amount());
        assertEquals(money("130.00"), updatedAuction.nextMinimumBid());
        assertEquals(money("880.00"), reloadedBuyer.getAvailableBalance());
        assertEquals(money("120.00"), reloadedBuyer.getHeldBalance());
        assertEquals(2, auctionEventRepository.findAll().size());
    }

    @Test
    void placeBidUpdatesListingDisplayPriceAfterQueueIsConsumed() {
        AuctionDetailResponse createdAuction = auctionService.createAuction(
            auctionRequest(true, 30L, "100.00", "150.00", "10.00"),
            seller.getId()
        );

        auctionService.placeBid(createdAuction.id(), new BidPlaceRequest(money("120.00")), buyer.getId());
        listingPriceUpdateQueue.flushPendingUpdates();

        Auction auction = auctionRepository.findById(createdAuction.id()).orElseThrow();
        Listing listing = listingRepository.findById(auction.getListing().getId()).orElseThrow();
        assertEquals(money("120.00"), listing.getPrice());
    }

    @Test
    void placeBidRejectsAuctionInDraftState() {
        AuctionDetailResponse createdAuction = auctionService.createAuction(
            auctionRequest(false, 30L, "100.00", "150.00", "10.00"),
            seller.getId()
        );

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
            auctionService.placeBid(createdAuction.id(), new BidPlaceRequest(money("120.00")), buyer.getId())
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void placeBidExtendsAuctionWhenBidArrivesNearDeadline() {
        AuctionDetailResponse createdAuction = auctionService.createAuction(
            auctionRequest(true, 10L, "100.00", "150.00", "10.00"),
            seller.getId()
        );

        mutableClock.advance(Duration.ofMinutes(9).plusSeconds(30));

        AuctionDetailResponse updatedAuction = auctionService.placeBid(
            createdAuction.id(),
            new BidPlaceRequest(money("120.00")),
            buyer.getId()
        );

        assertEquals(AuctionStatus.EXTENDED, updatedAuction.status());
        assertEquals(BASE_TIME.plus(Duration.ofMinutes(11).plusSeconds(30)), updatedAuction.endsAt());
        assertEquals(1, updatedAuction.extensionCount());
    }

    @Test
    void closingAuctionMarksWinnerWhenReservePriceIsMet() {
        AuctionDetailResponse createdAuction = auctionService.createAuction(
            auctionRequest(true, 5L, "100.00", "150.00", "10.00"),
            seller.getId()
        );

        auctionService.placeBid(createdAuction.id(), new BidPlaceRequest(money("160.00")), buyer.getId());
        mutableClock.advance(Duration.ofMinutes(6));

        AuctionDetailResponse resolvedAuction = auctionService.getAuctionDetail(createdAuction.id());
        User reloadedBuyer = userRepository.findById(buyer.getId()).orElseThrow();

        assertEquals(AuctionStatus.WON, resolvedAuction.status());
        assertNotNull(resolvedAuction.winningBid());
        assertEquals(money("160.00"), resolvedAuction.winningBid().amount());
        assertEquals(money("840.00"), reloadedBuyer.getAvailableBalance());
        assertEquals(money("0.00"), reloadedBuyer.getHeldBalance());
    }

    @Test
    void closingAuctionMarksUnsoldAndReleasesHeldFundsWhenReservePriceIsMissed() {
        AuctionDetailResponse createdAuction = auctionService.createAuction(
            auctionRequest(true, 5L, "100.00", "200.00", "10.00"),
            seller.getId()
        );

        auctionService.placeBid(createdAuction.id(), new BidPlaceRequest(money("150.00")), buyer.getId());
        mutableClock.advance(Duration.ofMinutes(6));

        AuctionDetailResponse resolvedAuction = auctionService.getAuctionDetail(createdAuction.id());
        User reloadedBuyer = userRepository.findById(buyer.getId()).orElseThrow();

        assertEquals(AuctionStatus.UNSOLD, resolvedAuction.status());
        assertNull(resolvedAuction.winningBid());
        assertEquals(money("1000.00"), reloadedBuyer.getAvailableBalance());
        assertEquals(money("0.00"), reloadedBuyer.getHeldBalance());
    }

    @Test
    void placingHigherBidReleasesPreviousLeaderFunds() {
        AuctionDetailResponse createdAuction = auctionService.createAuction(
            auctionRequest(true, 30L, "100.00", "150.00", "10.00"),
            seller.getId()
        );

        auctionService.placeBid(createdAuction.id(), new BidPlaceRequest(money("120.00")), buyer.getId());
        AuctionDetailResponse updatedAuction = auctionService.placeBid(
            createdAuction.id(),
            new BidPlaceRequest(money("130.00")),
            competingBuyer.getId()
        );

        User reloadedBuyer = userRepository.findById(buyer.getId()).orElseThrow();
        User reloadedCompetingBuyer = userRepository.findById(competingBuyer.getId()).orElseThrow();

        assertEquals(money("130.00"), updatedAuction.leadingBid().amount());
        assertEquals(money("1000.00"), reloadedBuyer.getAvailableBalance());
        assertEquals(money("0.00"), reloadedBuyer.getHeldBalance());
        assertEquals(money("870.00"), reloadedCompetingBuyer.getAvailableBalance());
        assertEquals(money("130.00"), reloadedCompetingBuyer.getHeldBalance());
    }

    @Test
    void placeBidRejectsAmountBelowNextMinimumBid() {
        AuctionDetailResponse createdAuction = auctionService.createAuction(
            auctionRequest(true, 30L, "100.00", "150.00", "10.00"),
            seller.getId()
        );

        auctionService.placeBid(createdAuction.id(), new BidPlaceRequest(money("120.00")), buyer.getId());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
            auctionService.placeBid(createdAuction.id(), new BidPlaceRequest(money("125.00")), competingBuyer.getId())
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("Bid must be at least 130.00", exception.getReason());
    }

    @Test
    void detailUsesSequenceNumberAsDeterministicTieBreakerWhenAmountsMatch() {
        AuctionDetailResponse createdAuction = auctionService.createAuction(
            auctionRequest(true, 30L, "100.00", "150.00", "10.00"),
            seller.getId()
        );

        Auction auction = auctionRepository.findById(createdAuction.id()).orElseThrow();
        Listing listing = auction.getListing();
        listing.setPrice(money("150.00"));
        auction.setNextBidSequence(3L);
        auctionRepository.save(auction);

        bidRepository.save(Bid.builder()
            .auction(auction)
            .bidder(buyer)
            .amount(money("150.00"))
            .sequenceNumber(1L)
            .submittedAt(BASE_TIME.plusSeconds(10))
            .build());
        bidRepository.save(Bid.builder()
            .auction(auction)
            .bidder(competingBuyer)
            .amount(money("150.00"))
            .sequenceNumber(2L)
            .submittedAt(BASE_TIME.plusSeconds(11))
            .build());

        AuctionDetailResponse detail = auctionService.getAuctionDetail(createdAuction.id());

        assertNotNull(detail.leadingBid());
        assertEquals(buyer.getId(), detail.leadingBid().bidderId());
        assertEquals(1L, detail.leadingBid().sequenceNumber());
    }

    @Test
    void activateAuctionShouldActivateDraftAuctionsAndRejectInvalidTransitions() {
        AuctionDetailResponse draftAuction = auctionService.createAuction(
            auctionRequest(false, 30L, "100.00", "150.00", "10.00"),
            seller.getId()
        );

        assertStatusAndReason(
            HttpStatus.FORBIDDEN,
            "Only the seller can manage this auction",
            () -> auctionService.activateAuction(draftAuction.id(), alternateSeller.getId())
        );

        AuctionDetailResponse activatedAuction = auctionService.activateAuction(draftAuction.id(), seller.getId());
        assertEquals(AuctionStatus.ACTIVE, activatedAuction.status());
        assertNotNull(activatedAuction.startsAt());
        assertNotNull(activatedAuction.endsAt());

        assertStatusAndReason(
            HttpStatus.CONFLICT,
            "Only draft auctions can be activated",
            () -> auctionService.activateAuction(draftAuction.id(), seller.getId())
        );
    }

    @Test
    void listAuctionsShouldReturnSummaryForActiveAndBidDrivenAuctions() {
        AuctionDetailResponse noBidAuction = auctionService.createAuction(
            auctionRequest(true, 30L, "100.00", "150.00", "10.00"),
            seller.getId()
        );
        AuctionDetailResponse bidAuction = auctionService.createAuction(
            new AuctionCreateRequest(
                "Arcade Stick",
                "Tournament-grade arcade stick",
                "https://img.example/arcade-stick.jpg",
                ListingCategory.HOBBIES,
                money("200.00"),
                money("250.00"),
                money("20.00"),
                45L,
                true
            ),
            seller.getId()
        );
        auctionService.placeBid(bidAuction.id(), new BidPlaceRequest(money("260.00")), buyer.getId());

        List<AuctionSummaryResponse> summaries = auctionService.listAuctions();
        AuctionSummaryResponse noBidSummary = summaries.stream()
            .filter(summary -> summary.id().equals(noBidAuction.id()))
            .findFirst()
            .orElseThrow();
        AuctionSummaryResponse bidSummary = summaries.stream()
            .filter(summary -> summary.id().equals(bidAuction.id()))
            .findFirst()
            .orElseThrow();

        assertEquals(money("100.00"), noBidSummary.currentPrice());
        assertEquals(money("100.00"), noBidSummary.nextMinimumBid());
        assertEquals(0L, noBidSummary.totalBids());

        assertEquals(money("260.00"), bidSummary.currentPrice());
        assertEquals(money("280.00"), bidSummary.nextMinimumBid());
        assertEquals(1L, bidSummary.totalBids());
    }

    @Test
    void getBidHistoryShouldReturnOrderedBidsAndWinningMarker() {
        AuctionDetailResponse createdAuction = auctionService.createAuction(
            auctionRequest(true, 30L, "100.00", "150.00", "10.00"),
            seller.getId()
        );

        auctionService.placeBid(createdAuction.id(), new BidPlaceRequest(money("120.00")), buyer.getId());
        auctionService.placeBid(createdAuction.id(), new BidPlaceRequest(money("130.00")), competingBuyer.getId());

        List<BidResponse> bidHistory = auctionService.getBidHistory(createdAuction.id());

        assertEquals(2, bidHistory.size());
        assertEquals(1L, bidHistory.get(0).sequenceNumber());
        assertFalse(bidHistory.get(0).winning());
        assertEquals(2L, bidHistory.get(1).sequenceNumber());
        assertTrue(bidHistory.get(1).winning());
    }

    @Test
    void closeAuctionShouldAllowManualClosureAndRejectInvalidClosureStates() {
        AuctionDetailResponse futureAuction = auctionService.createAuction(
            auctionRequest(true, 30L, "100.00", "150.00", "10.00"),
            seller.getId()
        );

        assertStatusAndReason(
            HttpStatus.CONFLICT,
            "Auction cannot be closed before its scheduled end",
            () -> auctionService.closeAuction(futureAuction.id(), seller.getId())
        );

        AuctionDetailResponse expiringAuction = auctionService.createAuction(
            new AuctionCreateRequest(
                "Vinyl Player",
                "Belt-driven turntable",
                "https://img.example/vinyl-player.jpg",
                ListingCategory.HOBBIES,
                money("150.00"),
                money("180.00"),
                money("10.00"),
                1L,
                true
            ),
            seller.getId()
        );
        auctionService.placeBid(expiringAuction.id(), new BidPlaceRequest(money("190.00")), buyer.getId());
        mutableClock.advance(Duration.ofMinutes(2));

        AuctionDetailResponse closedAuction = auctionService.closeAuction(expiringAuction.id(), seller.getId());
        assertEquals(AuctionStatus.WON, closedAuction.status());
        assertNotNull(closedAuction.closedAt());

        assertStatusAndReason(
            HttpStatus.CONFLICT,
            "Auction is already closed",
            () -> auctionService.closeAuction(expiringAuction.id(), seller.getId())
        );
    }

    @Test
    void createAuctionShouldRejectUnknownAndUnauthorizedSellers() {
        assertStatusAndReason(
            HttpStatus.UNAUTHORIZED,
            "User not found",
            () -> auctionService.createAuction(auctionRequest(true, 30L, "100.00", "150.00", "10.00"), UUID.randomUUID())
        );
        assertStatusAndReason(
            HttpStatus.FORBIDDEN,
            "Only SELLER can manage auctions",
            () -> auctionService.createAuction(auctionRequest(true, 30L, "100.00", "150.00", "10.00"), buyer.getId())
        );
    }

    @Test
    void createAuctionShouldRejectInvalidRequestsAndApplyDefaultValues() {
        assertStatusAndReason(
            HttpStatus.BAD_REQUEST,
            "Auction request is required",
            () -> auctionService.createAuction(null, seller.getId())
        );
        assertStatusAndReason(
            HttpStatus.BAD_REQUEST,
            "Title is required",
            () -> auctionService.createAuction(
                new AuctionCreateRequest(
                    " ",
                    "Valid",
                    null,
                    ListingCategory.OTHER,
                    money("100.00"),
                    money("150.00"),
                    money("10.00"),
                    30L,
                    true
                ),
                seller.getId()
            )
        );
        assertStatusAndReason(
            HttpStatus.BAD_REQUEST,
            "Description is required",
            () -> auctionService.createAuction(
                new AuctionCreateRequest(
                    "Valid",
                    " ",
                    null,
                    ListingCategory.OTHER,
                    money("100.00"),
                    money("150.00"),
                    money("10.00"),
                    30L,
                    true
                ),
                seller.getId()
            )
        );
        assertStatusAndReason(
            HttpStatus.BAD_REQUEST,
            "Starting price must be positive",
            () -> auctionService.createAuction(
                new AuctionCreateRequest(
                    "Valid",
                    "Valid",
                    null,
                    ListingCategory.OTHER,
                    BigDecimal.ZERO,
                    money("150.00"),
                    money("10.00"),
                    30L,
                    true
                ),
                seller.getId()
            )
        );
        assertStatusAndReason(
            HttpStatus.BAD_REQUEST,
            "Reserve price must be positive",
            () -> auctionService.createAuction(
                new AuctionCreateRequest(
                    "Valid",
                    "Valid",
                    null,
                    ListingCategory.OTHER,
                    money("100.00"),
                    BigDecimal.ZERO,
                    money("10.00"),
                    30L,
                    true
                ),
                seller.getId()
            )
        );
        assertStatusAndReason(
            HttpStatus.BAD_REQUEST,
            "Reserve price must be greater than or equal to starting price",
            () -> auctionService.createAuction(
                new AuctionCreateRequest(
                    "Valid",
                    "Valid",
                    null,
                    ListingCategory.OTHER,
                    money("100.00"),
                    money("90.00"),
                    money("10.00"),
                    30L,
                    true
                ),
                seller.getId()
            )
        );
        assertStatusAndReason(
            HttpStatus.BAD_REQUEST,
            "Minimum bid increment must be positive",
            () -> auctionService.createAuction(
                new AuctionCreateRequest(
                    "Valid",
                    "Valid",
                    null,
                    ListingCategory.OTHER,
                    money("100.00"),
                    money("150.00"),
                    BigDecimal.ZERO,
                    30L,
                    true
                ),
                seller.getId()
            )
        );
        assertStatusAndReason(
            HttpStatus.BAD_REQUEST,
            "Duration must be between 1 and 20160 minutes",
            () -> auctionService.createAuction(
                new AuctionCreateRequest(
                    "Valid",
                    "Valid",
                    null,
                    ListingCategory.OTHER,
                    money("100.00"),
                    money("150.00"),
                    money("10.00"),
                    0L,
                    true
                ),
                seller.getId()
            )
        );

        AuctionDetailResponse createdAuction = auctionService.createAuction(
            new AuctionCreateRequest(
                "Defaulted Auction",
                "Uses default increment and duration",
                "https://img.example/defaults.jpg",
                ListingCategory.OTHER,
                money("100.00"),
                money("150.00"),
                null,
                null,
                true
            ),
            seller.getId()
        );

        assertEquals(money("1.00"), createdAuction.minimumBidIncrement());
        assertEquals(60L, createdAuction.durationMinutes());
    }

    @Test
    void getAuctionDetailAndActivateShouldRejectUnknownAuctionIds() {
        assertStatusAndReason(
            HttpStatus.NOT_FOUND,
            "Auction not found",
            () -> auctionService.getAuctionDetail(UUID.randomUUID())
        );
        assertStatusAndReason(
            HttpStatus.NOT_FOUND,
            "Auction not found",
            () -> auctionService.activateAuction(UUID.randomUUID(), seller.getId())
        );
    }

    @Test
    void placeBidShouldRejectInvalidRequestsAndInvalidBidders() {
        AuctionDetailResponse createdAuction = auctionService.createAuction(
            auctionRequest(true, 30L, "100.00", "150.00", "10.00"),
            seller.getId()
        );

        assertStatusAndReason(
            HttpStatus.BAD_REQUEST,
            "Bid amount is required",
            () -> auctionService.placeBid(createdAuction.id(), null, buyer.getId())
        );
        assertStatusAndReason(
            HttpStatus.BAD_REQUEST,
            "Bid amount is required",
            () -> auctionService.placeBid(createdAuction.id(), new BidPlaceRequest(null), buyer.getId())
        );
        assertStatusAndReason(
            HttpStatus.BAD_REQUEST,
            "Bid amount must be positive",
            () -> auctionService.placeBid(createdAuction.id(), new BidPlaceRequest(BigDecimal.ZERO), buyer.getId())
        );
        assertStatusAndReason(
            HttpStatus.UNAUTHORIZED,
            "User not found",
            () -> auctionService.placeBid(createdAuction.id(), new BidPlaceRequest(money("120.00")), UUID.randomUUID())
        );
        assertStatusAndReason(
            HttpStatus.FORBIDDEN,
            "Only BUYER can place bids",
            () -> auctionService.placeBid(createdAuction.id(), new BidPlaceRequest(money("120.00")), alternateSeller.getId())
        );
    }

    @Test
    void placeBidShouldSupportSameBuyerIncreasingLeadingBid() {
        AuctionDetailResponse createdAuction = auctionService.createAuction(
            auctionRequest(true, 30L, "100.00", "150.00", "10.00"),
            seller.getId()
        );

        auctionService.placeBid(createdAuction.id(), new BidPlaceRequest(money("120.00")), buyer.getId());
        AuctionDetailResponse updatedAuction = auctionService.placeBid(
            createdAuction.id(),
            new BidPlaceRequest(money("140.00")),
            buyer.getId()
        );

        User reloadedBuyer = userRepository.findById(buyer.getId()).orElseThrow();
        assertEquals(money("140.00"), updatedAuction.leadingBid().amount());
        assertEquals(money("860.00"), reloadedBuyer.getAvailableBalance());
        assertEquals(money("140.00"), reloadedBuyer.getHeldBalance());
    }

    @Test
    void reflectionGuardsShouldHandleUnsupportedAuctionStates() {
        Auction closedAuction = Auction.builder()
            .status(AuctionStatus.CLOSED)
            .build();
        Auction auctionWithoutEndTime = Auction.builder()
            .status(AuctionStatus.ACTIVE)
            .listing(Listing.builder().seller(seller).build())
            .build();
        Auction sellerOwnedAuction = Auction.builder()
            .status(AuctionStatus.ACTIVE)
            .endsAt(BASE_TIME.plusSeconds(30))
            .listing(Listing.builder().seller(seller).build())
            .build();

        ReflectionTestUtils.invokeMethod(auctionService, "closeAuctionInternal", closedAuction, BASE_TIME);
        ReflectionTestUtils.invokeMethod(auctionService, "extendAuctionIfNeeded", auctionWithoutEndTime, BASE_TIME);

        assertStatusAndReason(
            HttpStatus.CONFLICT,
            "Auction is not accepting bids",
            () -> ReflectionTestUtils.invokeMethod(
                auctionService,
                "ensureAuctionAcceptsBid",
                Auction.builder().status(AuctionStatus.CLOSED).listing(Listing.builder().seller(seller).build()).build(),
                buyer.getId()
            )
        );
        assertStatusAndReason(
            HttpStatus.CONFLICT,
            "Auction has no valid end time",
            () -> ReflectionTestUtils.invokeMethod(auctionService, "ensureAuctionAcceptsBid", auctionWithoutEndTime, buyer.getId())
        );
        assertStatusAndReason(
            HttpStatus.FORBIDDEN,
            "Seller cannot bid on their own auction",
            () -> ReflectionTestUtils.invokeMethod(auctionService, "ensureAuctionAcceptsBid", sellerOwnedAuction, seller.getId())
        );

        assertNull(auctionWithoutEndTime.getEndsAt());
        assertEquals(AuctionStatus.CLOSED, closedAuction.getStatus());
    }

    private AuctionCreateRequest auctionRequest(
        boolean activateNow,
        long durationMinutes,
        String startingPrice,
        String reservePrice,
        String minimumBidIncrement
    ) {
        return new AuctionCreateRequest(
            "Mechanical Keyboard",
            "Hot-swappable keyboard with custom switches",
            "https://img.example/keyboard.jpg",
            ListingCategory.ELECTRONICS,
            money(startingPrice),
            money(reservePrice),
            money(minimumBidIncrement),
            durationMinutes,
            activateNow
        );
    }

    private User saveUser(Role role, BigDecimal availableBalance) {
        return userRepository.save(User.builder()
            .email(role.name().toLowerCase() + "-" + java.util.UUID.randomUUID() + "@bidmart.test")
            .passwordHash("hash")
            .role(role)
            .availableBalance(availableBalance)
            .heldBalance(money("0.00"))
            .createdAt(BASE_TIME)
            .build());
    }

    private static BigDecimal money(String amount) {
        return new BigDecimal(amount).setScale(2, RoundingMode.HALF_UP);
    }

    private void assertStatusAndReason(HttpStatus expectedStatus, String expectedReason, ThrowingRunnable executable) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, executable::run);
        assertEquals(expectedStatus, exception.getStatusCode());
        assertEquals(expectedReason, exception.getReason());
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }

    @TestConfiguration
    static class ClockTestConfiguration {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(BASE_TIME, ZoneOffset.UTC);
        }
    }

    static class MutableClock extends Clock {

        private Instant instant;
        private final ZoneId zoneId;

        MutableClock(Instant instant, ZoneId zoneId) {
            this.instant = instant;
            this.zoneId = zoneId;
        }

        void setInstant(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zoneId;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
