package id.ac.ui.cs.advprog.backend;

import id.ac.ui.cs.advprog.backend.model.Auction;
import id.ac.ui.cs.advprog.backend.model.AuctionStatus;
import id.ac.ui.cs.advprog.backend.model.Bid;
import id.ac.ui.cs.advprog.backend.model.Listing;
import id.ac.ui.cs.advprog.backend.model.ListingCategory;
import id.ac.ui.cs.advprog.backend.model.Role;
import id.ac.ui.cs.advprog.backend.model.User;
import id.ac.ui.cs.advprog.backend.repository.AuctionRepository;
import id.ac.ui.cs.advprog.backend.repository.BidRepository;
import id.ac.ui.cs.advprog.backend.repository.ListingRepository;
import id.ac.ui.cs.advprog.backend.repository.UserRepository;
import id.ac.ui.cs.advprog.backend.security.JwtService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "security.jwt.secret=test-secret-please-change-32-chars",
    "security.jwt.expiration-seconds=3600"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarketplaceFoundationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ListingRepository listingRepository;

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private BidRepository bidRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        bidRepository.deleteAll();
        auctionRepository.deleteAll();
        listingRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void createListingShouldRejectUnauthenticatedRequest() throws Exception {
        mockMvc.perform(post("/api/listings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "Mechanical Keyboard",
                      "description": "Hot-swappable keyboard",
                      "imageUrl": "https://img.example/keyboard.jpg",
                      "price": 1500000,
                      "category": "ELECTRONICS"
                    }
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    @Test
    void createListingShouldAllowSellerWithImageAndHierarchyCategory() throws Exception {
        User seller = createUser("seller@example.com", "password123", Role.SELLER);

        mockMvc.perform(post("/api/listings")
                .header("Authorization", bearerToken(seller))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "Gaming Phone",
                      "description": "Flagship smartphone",
                      "imageUrl": "https://img.example/phone.jpg",
                      "price": 12500000,
                      "category": "ELECTRONICS_SMARTPHONE"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("Gaming Phone"))
            .andExpect(jsonPath("$.imageUrl").value("https://img.example/phone.jpg"))
            .andExpect(jsonPath("$.category").value("ELECTRONICS_SMARTPHONE"))
            .andExpect(jsonPath("$.categoryPath").value("Elektronik > Handphone > Smartphone"))
            .andExpect(jsonPath("$.sellerId").value(seller.getId().toString()))
            .andExpect(jsonPath("$.sellerEmail").value("seller@example.com"));
    }

    @Test
    void createListingShouldRejectBuyer() throws Exception {
        User buyer = createUser("buyer@example.com", "password123", Role.BUYER);

        mockMvc.perform(post("/api/listings")
                .header("Authorization", bearerToken(buyer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "Mechanical Keyboard",
                      "description": "Hot-swappable keyboard",
                      "price": 1500000,
                      "category": "ELECTRONICS"
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void createListingShouldRejectInvalidPayload() throws Exception {
        User seller = createUser("seller@example.com", "password123", Role.SELLER);

        mockMvc.perform(post("/api/listings")
                .header("Authorization", bearerToken(seller))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "",
                      "description": "",
                      "price": 0,
                      "category": "OTHER"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Request validation failed"))
            .andExpect(jsonPath("$.fieldErrors.title").value("Title is required"))
            .andExpect(jsonPath("$.fieldErrors.description").value("Description is required"))
            .andExpect(jsonPath("$.fieldErrors.price").value("Price must be positive"));
    }

    @Test
    void getListingDetailShouldReturnSellerAndAuctionMetadata() throws Exception {
        User seller = createUser("seller@example.com", "password123", Role.SELLER);
        Listing listing = saveListing(seller, "Camera", "Mirrorless camera", ListingCategory.ELECTRONICS, "2500000.00");
        Auction auction = createAuctionFor(listing, AuctionStatus.ACTIVE, Instant.now().plus(2, ChronoUnit.HOURS));

        mockMvc.perform(get("/api/listings/{listingId}", listing.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(listing.getId().toString()))
            .andExpect(jsonPath("$.sellerId").value(seller.getId().toString()))
            .andExpect(jsonPath("$.sellerEmail").value("seller@example.com"))
            .andExpect(jsonPath("$.auctionId").value(auction.getId().toString()))
            .andExpect(jsonPath("$.auctionStatus").value("ACTIVE"));
    }

    @Test
    void updateListingShouldAllowOwnerBeforeBid() throws Exception {
        User seller = createUser("seller@example.com", "password123", Role.SELLER);
        Listing listing = saveListing(seller, "Old title", "Old description", ListingCategory.BOOKS, "100.00");

        mockMvc.perform(put("/api/listings/{listingId}", listing.getId())
                .header("Authorization", bearerToken(seller))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "description": "New description",
                      "imageUrl": "https://img.example/new-book.jpg",
                      "category": "BOOKS_FICTION"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").value("New description"))
            .andExpect(jsonPath("$.imageUrl").value("https://img.example/new-book.jpg"))
            .andExpect(jsonPath("$.category").value("BOOKS_FICTION"))
            .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void updateListingShouldRejectWhenListingAlreadyHasBid() throws Exception {
        User seller = createUser("seller@example.com", "password123", Role.SELLER);
        User buyer = createUser("buyer@example.com", "password123", Role.BUYER);
        Listing listing = saveListing(seller, "Console", "Retro console", ListingCategory.HOBBIES, "500.00");
        Auction auction = createAuctionFor(listing, AuctionStatus.ACTIVE, Instant.now().plus(1, ChronoUnit.HOURS));
        createBid(auction, buyer, "550.00", 1L);

        mockMvc.perform(put("/api/listings/{listingId}", listing.getId())
                .header("Authorization", bearerToken(seller))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "description": "Updated description",
                      "imageUrl": "https://img.example/console.jpg",
                      "category": "HOBBIES"
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Listing cannot be modified because it already has bids"));
    }

    @Test
    void cancelListingShouldMarkListingAsCancelledAndCloseAuction() throws Exception {
        User seller = createUser("seller@example.com", "password123", Role.SELLER);
        Listing listing = saveListing(seller, "Camera", "Mirrorless camera", ListingCategory.ELECTRONICS, "2500000.00");
        createAuctionFor(listing, AuctionStatus.ACTIVE, Instant.now().plus(3, ChronoUnit.HOURS));

        mockMvc.perform(delete("/api/listings/{listingId}", listing.getId())
                .header("Authorization", bearerToken(seller)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.auctionStatus").value("CLOSED"))
            .andExpect(jsonPath("$.cancelledAt").isNotEmpty());
    }

    @Test
    void cancelListingShouldRejectWhenListingAlreadyHasBid() throws Exception {
        User seller = createUser("seller@example.com", "password123", Role.SELLER);
        User buyer = createUser("buyer@example.com", "password123", Role.BUYER);
        Listing listing = saveListing(seller, "Tablet", "Drawing tablet", ListingCategory.ELECTRONICS, "1500.00");
        Auction auction = createAuctionFor(listing, AuctionStatus.ACTIVE, Instant.now().plus(2, ChronoUnit.HOURS));
        createBid(auction, buyer, "1600.00", 1L);

        mockMvc.perform(delete("/api/listings/{listingId}", listing.getId())
                .header("Authorization", bearerToken(seller)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Listing cannot be modified because it already has bids"));
    }

    @Test
    void listListingsShouldHandleZeroPageSizeSafely() throws Exception {
        User seller = createUser("seller@example.com", "password123", Role.SELLER);
        saveListing(seller, "Camera", "Mirrorless camera", ListingCategory.ELECTRONICS, "2500000.00");

        mockMvc.perform(get("/api/listings").param("size", "0"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].title").value("Camera"));
    }

    @Test
    void listListingsShouldFilterByCategoryKeywordPriceAndEndingWindow() throws Exception {
        User seller = createUser("seller@example.com", "password123", Role.SELLER);
        Listing matchingListing = saveListing(
            seller,
            "Gaming Phone",
            "Competitive smartphone",
            ListingCategory.ELECTRONICS_SMARTPHONE,
            "1200.00"
        );
        createAuctionFor(matchingListing, AuctionStatus.ACTIVE, Instant.parse("2026-04-20T10:00:00Z"));

        Listing otherListing = saveListing(
            seller,
            "Novel Book",
            "Fiction story",
            ListingCategory.BOOKS_FICTION,
            "25.00"
        );
        createAuctionFor(otherListing, AuctionStatus.ACTIVE, Instant.parse("2026-04-25T10:00:00Z"));

        mockMvc.perform(get("/api/listings")
                .param("category", "ELECTRONICS")
                .param("keyword", "phone")
                .param("minPrice", "1000")
                .param("maxPrice", "1500")
                .param("endingAfter", "2026-04-20T00:00:00Z")
                .param("endingBefore", "2026-04-21T00:00:00Z"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].title").value("Gaming Phone"))
            .andExpect(jsonPath("$[0].category").value("ELECTRONICS_SMARTPHONE"));
    }

    @Test
    void listListingsShouldRejectInvalidPriceRange() throws Exception {
        mockMvc.perform(get("/api/listings")
                .param("minPrice", "200")
                .param("maxPrice", "100"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("minPrice cannot be greater than maxPrice"));
    }

    @Test
    void listCategoriesTreeShouldReturnHierarchy() throws Exception {
        mockMvc.perform(get("/api/listings/categories/tree"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].key").value("ELECTRONICS"))
            .andExpect(jsonPath("$[0].children[0].key").value("ELECTRONICS_PHONE"))
            .andExpect(jsonPath("$[0].children[0].children[0].key").value("ELECTRONICS_SMARTPHONE"));
    }

    @Test
    void publicSellerProfileShouldExposeListingAndAuctionCounts() throws Exception {
        User seller = createUser("seller@example.com", "password123", Role.SELLER);
        Listing listingA = saveListing(seller, "Phone", "First", ListingCategory.ELECTRONICS, "100.00");
        Listing listingB = saveListing(seller, "Laptop", "Second", ListingCategory.ELECTRONICS_LAPTOP, "200.00");
        createAuctionFor(listingA, AuctionStatus.ACTIVE, Instant.now().plus(1, ChronoUnit.DAYS));
        createAuctionFor(listingB, AuctionStatus.WON, Instant.now().minus(1, ChronoUnit.DAYS));

        mockMvc.perform(get("/api/users/{userId}/public-profile", seller.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(seller.getId().toString()))
            .andExpect(jsonPath("$.email").value("seller@example.com"))
            .andExpect(jsonPath("$.activeListingCount").value(2))
            .andExpect(jsonPath("$.liveAuctionCount").value(1))
            .andExpect(jsonPath("$.completedAuctionCount").value(1));
    }

    @Test
    void validateListingForBidShouldReturnAuctionValidationState() throws Exception {
        User seller = createUser("seller@example.com", "password123", Role.SELLER);
        Listing listing = saveListing(seller, "Watch", "Sport watch", ListingCategory.SPORTS, "300.00");
        createAuctionFor(listing, AuctionStatus.ACTIVE, Instant.now().plus(1, ChronoUnit.DAYS));

        mockMvc.perform(get("/api/listings/{listingId}/validation", listing.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.listingId").value(listing.getId().toString()))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.biddable").value(true))
            .andExpect(jsonPath("$.auctionStatus").value("ACTIVE"));
    }

    private User createUser(String email, String rawPassword, Role role) {
        return userRepository.save(User.builder()
            .email(email)
            .passwordHash(passwordEncoder.encode(rawPassword))
            .role(role)
            .build());
    }

    private Listing saveListing(
        User seller,
        String title,
        String description,
        ListingCategory category,
        String price
    ) {
        return listingRepository.save(Listing.builder()
            .title(title)
            .description(description)
            .imageUrl("https://img.example/" + title.toLowerCase().replace(" ", "-") + ".jpg")
            .price(new BigDecimal(price))
            .category(category)
            .seller(seller)
            .build());
    }

    private Auction createAuctionFor(Listing listing, AuctionStatus status, Instant endsAt) {
        Listing managedListing = listingRepository.findById(listing.getId()).orElseThrow();
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return auctionRepository.save(Auction.builder()
            .listing(managedListing)
            .status(status)
            .startingPrice(managedListing.getPrice())
            .reservePrice(managedListing.getPrice())
            .minimumBidIncrement(new BigDecimal("10.00"))
            .durationMinutes(60L)
            .createdAt(createdAt)
            .startsAt(createdAt)
            .endsAt(endsAt)
            .build());
    }

    private Bid createBid(Auction auction, User bidder, String amount, long sequenceNumber) {
        return bidRepository.save(Bid.builder()
            .auction(auction)
            .bidder(bidder)
            .amount(new BigDecimal(amount))
            .sequenceNumber(sequenceNumber)
            .submittedAt(Instant.now())
            .build());
    }

    private String bearerToken(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }
}
