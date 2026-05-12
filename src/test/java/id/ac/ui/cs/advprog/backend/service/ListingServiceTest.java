package id.ac.ui.cs.advprog.backend.service;

import id.ac.ui.cs.advprog.backend.dto.ListingBidValidationResponse;
import id.ac.ui.cs.advprog.backend.dto.ListingCreateRequest;
import id.ac.ui.cs.advprog.backend.dto.ListingDetailResponse;
import id.ac.ui.cs.advprog.backend.dto.ListingResponse;
import id.ac.ui.cs.advprog.backend.dto.ListingUpdateRequest;
import id.ac.ui.cs.advprog.backend.dto.PublicSellerProfileResponse;
import id.ac.ui.cs.advprog.backend.model.Auction;
import id.ac.ui.cs.advprog.backend.model.AuctionStatus;
import id.ac.ui.cs.advprog.backend.model.Bid;
import id.ac.ui.cs.advprog.backend.model.Listing;
import id.ac.ui.cs.advprog.backend.model.ListingCategory;
import id.ac.ui.cs.advprog.backend.model.ListingStatus;
import id.ac.ui.cs.advprog.backend.model.Role;
import id.ac.ui.cs.advprog.backend.model.User;
import id.ac.ui.cs.advprog.backend.repository.AuctionRepository;
import id.ac.ui.cs.advprog.backend.repository.BidRepository;
import id.ac.ui.cs.advprog.backend.repository.ListingRepository;
import id.ac.ui.cs.advprog.backend.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

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
class ListingServiceTest {

    @Autowired
    private ListingService listingService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ListingRepository listingRepository;

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private BidRepository bidRepository;

    private User seller;
    private User sellerTwo;
    private User buyer;

    @BeforeEach
    void setUp() {
        bidRepository.deleteAll();
        auctionRepository.deleteAll();
        listingRepository.deleteAll();
        userRepository.deleteAll();

        seller = saveUser(Role.SELLER);
        sellerTwo = saveUser(Role.SELLER);
        buyer = saveUser(Role.BUYER);
    }

    @Test
    void createListingShouldTrimFieldsAndDefaultToOtherCategory() {
        ListingResponse created = listingService.createListing(
            new ListingCreateRequest(
                "  Collector Figure  ",
                "  Mint in box  ",
                "   ",
                new BigDecimal("250.00"),
                null
            ),
            seller.getId()
        );

        assertEquals("Collector Figure", created.title());
        assertEquals("Mint in box", created.description());
        assertNull(created.imageUrl());
        assertEquals(ListingCategory.OTHER, created.category());
        assertEquals("Lainnya", created.categoryPath());
    }

    @Test
    void createListingShouldRejectInvalidInputAndUnauthorizedSeller() {
        assertStatusAndReason(
            HttpStatus.BAD_REQUEST,
            "Listing request is required",
            () -> listingService.createListing(null, seller.getId())
        );
        assertStatusAndReason(
            HttpStatus.BAD_REQUEST,
            "Title is required",
            () -> listingService.createListing(
                new ListingCreateRequest(" ", "Valid", null, new BigDecimal("1.00"), ListingCategory.OTHER),
                seller.getId()
            )
        );
        assertStatusAndReason(
            HttpStatus.BAD_REQUEST,
            "Description is required",
            () -> listingService.createListing(
                new ListingCreateRequest("Valid", " ", null, new BigDecimal("1.00"), ListingCategory.OTHER),
                seller.getId()
            )
        );
        assertStatusAndReason(
            HttpStatus.BAD_REQUEST,
            "Price must be positive",
            () -> listingService.createListing(
                new ListingCreateRequest("Valid", "Valid", null, BigDecimal.ZERO, ListingCategory.OTHER),
                seller.getId()
            )
        );
        assertStatusAndReason(
            HttpStatus.UNAUTHORIZED,
            "User not found",
            () -> listingService.createListing(
                new ListingCreateRequest("Valid", "Valid", null, new BigDecimal("1.00"), ListingCategory.OTHER),
                UUID.randomUUID()
            )
        );
        assertStatusAndReason(
            HttpStatus.FORBIDDEN,
            "Only SELLER can create listings",
            () -> listingService.createListing(
                new ListingCreateRequest("Valid", "Valid", null, new BigDecimal("1.00"), ListingCategory.OTHER),
                buyer.getId()
            )
        );
    }

    @Test
    void getListingDetailShouldRejectMissingAndInactiveListing() {
        Listing inactiveListing = saveListing(
            seller,
            "Lamp",
            "Desk lamp",
            ListingCategory.HOME_LIVING,
            "25.00",
            ListingStatus.CANCELLED,
            "https://img.example/lamp.jpg"
        );

        assertStatusAndReason(
            HttpStatus.NOT_FOUND,
            "Listing not found",
            () -> listingService.getListingDetail(UUID.randomUUID())
        );
        assertStatusAndReason(
            HttpStatus.NOT_FOUND,
            "Listing not found",
            () -> listingService.getListingDetail(inactiveListing.getId())
        );
    }

    @Test
    void getAllListingsShouldRespectSortingPagingAndAuctionWindowFilters() {
        Listing alphaListing = saveListing(
            seller,
            "Alpha Phone",
            "Competitive smartphone",
            ListingCategory.ELECTRONICS_SMARTPHONE,
            "1000.00",
            ListingStatus.ACTIVE,
            "https://img.example/alpha.jpg"
        );
        Listing betaListing = saveListing(
            seller,
            "Beta Laptop",
            "Portable workstation",
            ListingCategory.ELECTRONICS_LAPTOP,
            "2000.00",
            ListingStatus.ACTIVE,
            "https://img.example/beta.jpg"
        );
        Listing plainListing = saveListing(
            seller,
            "Gamma Book",
            "No auction attached",
            ListingCategory.BOOKS,
            "20.00",
            ListingStatus.ACTIVE,
            "https://img.example/gamma.jpg"
        );

        createAuction(alphaListing, AuctionStatus.ACTIVE, Instant.parse("2026-04-21T10:00:00Z"), null);
        createAuction(betaListing, AuctionStatus.CLOSED, Instant.parse("2026-04-22T10:00:00Z"), Instant.parse("2026-04-22T11:00:00Z"));

        List<ListingResponse> sortedListings = listingService.getAllListings(
            PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "title")),
            ListingCategory.ELECTRONICS,
            null,
            null,
            null,
            null,
            null
        );

        assertEquals(List.of("Alpha Phone", "Beta Laptop"), sortedListings.stream().map(ListingResponse::title).toList());
        assertNotNull(sortedListings.getFirst().endsAt());

        List<ListingResponse> endingAfterListings = listingService.getAllListings(
            Pageable.unpaged(),
            null,
            null,
            null,
            null,
            Instant.parse("2026-04-22T00:00:00Z"),
            null
        );
        List<ListingResponse> endingBeforeListings = listingService.getAllListings(
            Pageable.unpaged(),
            null,
            null,
            null,
            null,
            null,
            Instant.parse("2026-04-21T23:59:59Z")
        );

        assertEquals(List.of(betaListing.getId()), endingAfterListings.stream().map(ListingResponse::id).toList());
        assertEquals(List.of(alphaListing.getId()), endingBeforeListings.stream().map(ListingResponse::id).toList());
        assertFalse(endingAfterListings.stream().anyMatch(listing -> listing.id().equals(plainListing.getId())));
    }

    @Test
    void updateAndCancelListingShouldEnforceOwnershipAndStateRules() {
        Listing activeListing = saveListing(
            seller,
            "Gaming Console",
            "Bundle package",
            ListingCategory.HOBBIES,
            "350.00",
            ListingStatus.ACTIVE,
            "https://img.example/console.jpg"
        );
        Listing inactiveListing = saveListing(
            seller,
            "Archive Magazine",
            "Vintage issue",
            ListingCategory.BOOKS,
            "15.00",
            ListingStatus.CANCELLED,
            "https://img.example/magazine.jpg"
        );

        assertStatusAndReason(
            HttpStatus.BAD_REQUEST,
            "Listing update request is required",
            () -> listingService.updateListing(activeListing.getId(), null, seller.getId())
        );
        assertStatusAndReason(
            HttpStatus.NOT_FOUND,
            "Listing not found",
            () -> listingService.updateListing(UUID.randomUUID(), validUpdateRequest(), seller.getId())
        );
        assertStatusAndReason(
            HttpStatus.FORBIDDEN,
            "You do not own this listing",
            () -> listingService.updateListing(activeListing.getId(), validUpdateRequest(), sellerTwo.getId())
        );
        assertStatusAndReason(
            HttpStatus.CONFLICT,
            "Listing is not active",
            () -> listingService.updateListing(inactiveListing.getId(), validUpdateRequest(), seller.getId())
        );
        assertStatusAndReason(
            HttpStatus.BAD_REQUEST,
            "Description is required",
            () -> listingService.updateListing(
                activeListing.getId(),
                new ListingUpdateRequest(" ", "https://img.example/updated.jpg", ListingCategory.OTHER),
                seller.getId()
            )
        );

        Listing auctionListing = saveListing(
            seller,
            "Limited Sneakers",
            "Deadstock pair",
            ListingCategory.FASHION_MENSWEAR,
            "500.00",
            ListingStatus.ACTIVE,
            "https://img.example/sneakers.jpg"
        );
        createAuction(auctionListing, AuctionStatus.EXTENDED, Instant.parse("2026-04-23T10:00:00Z"), null);

        ListingDetailResponse cancelled = listingService.cancelListing(auctionListing.getId(), seller.getId());

        assertEquals(ListingStatus.CANCELLED, cancelled.status());
        assertEquals(AuctionStatus.CLOSED, cancelled.auctionStatus());
        assertNotNull(cancelled.closedAt());
    }

    @Test
    void publicSellerProfileAndBidValidationShouldHandleNonSellerInactiveAuctionlessAndClosedStates() {
        Listing inactiveListing = saveListing(
            seller,
            "Old Camera",
            "Retired stock",
            ListingCategory.ELECTRONICS,
            "300.00",
            ListingStatus.CANCELLED,
            "https://img.example/old-camera.jpg"
        );
        createAuction(inactiveListing, AuctionStatus.CLOSED, Instant.parse("2026-04-20T10:00:00Z"), Instant.parse("2026-04-20T10:30:00Z"));

        Listing activeWithoutAuction = saveListing(
            seller,
            "Poster",
            "Signed poster",
            ListingCategory.OTHER,
            "40.00",
            ListingStatus.ACTIVE,
            "https://img.example/poster.jpg"
        );
        Listing activeClosedAuction = saveListing(
            seller,
            "Drone",
            "Foldable drone",
            ListingCategory.ELECTRONICS,
            "800.00",
            ListingStatus.ACTIVE,
            "https://img.example/drone.jpg"
        );
        createAuction(activeClosedAuction, AuctionStatus.CLOSED, Instant.parse("2026-04-22T10:00:00Z"), Instant.parse("2026-04-22T10:30:00Z"));

        assertStatusAndReason(
            HttpStatus.NOT_FOUND,
            "Seller not found",
            () -> listingService.getPublicSellerProfile(buyer.getId())
        );
        assertStatusAndReason(
            HttpStatus.NOT_FOUND,
            "Listing not found",
            () -> listingService.validateListingForBid(UUID.randomUUID())
        );

        PublicSellerProfileResponse publicProfile = listingService.getPublicSellerProfile(seller.getId());
        ListingBidValidationResponse inactiveValidation = listingService.validateListingForBid(inactiveListing.getId());
        ListingBidValidationResponse noAuctionValidation = listingService.validateListingForBid(activeWithoutAuction.getId());
        ListingBidValidationResponse closedAuctionValidation = listingService.validateListingForBid(activeClosedAuction.getId());

        assertEquals(seller.getId(), publicProfile.id());
        assertEquals(Role.SELLER, publicProfile.role());
        assertEquals(2, publicProfile.activeListingCount());
        assertEquals(0, publicProfile.liveAuctionCount());
        assertEquals(2, publicProfile.completedAuctionCount());

        assertFalse(inactiveValidation.active());
        assertFalse(inactiveValidation.biddable());
        assertEquals("Listing is no longer active", inactiveValidation.message());
        assertEquals(AuctionStatus.CLOSED, inactiveValidation.auctionStatus());
        assertNotNull(inactiveValidation.endsAt());

        assertTrue(noAuctionValidation.active());
        assertFalse(noAuctionValidation.biddable());
        assertEquals("Listing is not attached to an auction", noAuctionValidation.message());
        assertNull(noAuctionValidation.auctionStatus());
        assertNull(noAuctionValidation.endsAt());

        assertTrue(closedAuctionValidation.active());
        assertFalse(closedAuctionValidation.biddable());
        assertEquals("Auction is not accepting bids", closedAuctionValidation.message());
        assertEquals(AuctionStatus.CLOSED, closedAuctionValidation.auctionStatus());
    }

    @Test
    void updateDisplayedPriceShouldIgnoreMissingListingsAndUpdateExistingRecords() {
        Listing listing = saveListing(
            seller,
            "Mechanical Keyboard",
            "Hot-swappable",
            ListingCategory.ELECTRONICS,
            "120.00",
            ListingStatus.ACTIVE,
            "https://img.example/keyboard.jpg"
        );

        listingService.updateDisplayedPrice(UUID.randomUUID(), new BigDecimal("150.00"));
        listingService.updateDisplayedPrice(listing.getId(), new BigDecimal("180.00"));

        Listing reloadedListing = listingRepository.findById(listing.getId()).orElseThrow();
        assertEquals(new BigDecimal("180.00"), reloadedListing.getPrice());
        assertNotNull(reloadedListing.getUpdatedAt());
    }

    @Test
    void updateListingShouldRejectWhenListingAlreadyHasBid() {
        Listing listing = saveListing(
            seller,
            "Rare Card",
            "Mint condition",
            ListingCategory.HOBBIES,
            "250.00",
            ListingStatus.ACTIVE,
            "https://img.example/card.jpg"
        );
        Auction auction = createAuction(listing, AuctionStatus.ACTIVE, Instant.now().plus(1, ChronoUnit.DAYS), null);
        bidRepository.save(Bid.builder()
            .auction(auction)
            .bidder(buyer)
            .amount(new BigDecimal("260.00"))
            .sequenceNumber(1L)
            .submittedAt(Instant.now())
            .build());

        assertStatusAndReason(
            HttpStatus.CONFLICT,
            "Listing cannot be modified because it already has bids",
            () -> listingService.updateListing(listing.getId(), validUpdateRequest(), seller.getId())
        );
    }

    private ListingUpdateRequest validUpdateRequest() {
        return new ListingUpdateRequest(
            "Updated description",
            "   ",
            ListingCategory.BOOKS_FICTION
        );
    }

    private void assertStatusAndReason(HttpStatus expectedStatus, String expectedReason, ThrowingRunnable executable) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, executable::run);
        assertEquals(expectedStatus, exception.getStatusCode());
        assertEquals(expectedReason, exception.getReason());
    }

    private User saveUser(Role role) {
        return userRepository.save(User.builder()
            .email(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@bidmart.test")
            .passwordHash("hash")
            .role(role)
            .availableBalance(new BigDecimal("1000.00"))
            .heldBalance(BigDecimal.ZERO)
            .createdAt(Instant.now())
            .build());
    }

    private Listing saveListing(
        User owner,
        String title,
        String description,
        ListingCategory category,
        String price,
        ListingStatus status,
        String imageUrl
    ) {
        return listingRepository.save(Listing.builder()
            .title(title)
            .description(description)
            .imageUrl(imageUrl)
            .price(new BigDecimal(price))
            .category(category)
            .status(status)
            .seller(owner)
            .createdAt(Instant.now())
            .build());
    }

    private Auction createAuction(Listing listing, AuctionStatus status, Instant endsAt, Instant closedAt) {
        return auctionRepository.save(Auction.builder()
            .listing(listingRepository.findById(listing.getId()).orElseThrow())
            .status(status)
            .startingPrice(listing.getPrice())
            .reservePrice(listing.getPrice())
            .minimumBidIncrement(new BigDecimal("10.00"))
            .durationMinutes(60L)
            .createdAt(Instant.now())
            .startsAt(Instant.now())
            .endsAt(endsAt)
            .closedAt(closedAt)
            .build());
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }
}
