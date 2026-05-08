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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class AuctionReadIntegrationTest {

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

    @BeforeEach
    void setUp() {
        bidRepository.deleteAll();
        auctionRepository.deleteAll();
        listingRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void listAuctionsShouldReturnFrontendVisibleAuctionSummary() throws Exception {
        User seller = createUser("seller@example.com", Role.SELLER);
        Listing listing = saveListing(seller, "Gaming Phone", "Competitive smartphone", "1200.00");
        Auction auction = saveAuction(listing, AuctionStatus.ACTIVE, Instant.now().plus(2, ChronoUnit.HOURS));
        createBid(auction, createUser("buyer@example.com", Role.BUYER), "1250.00", 1L);

        mockMvc.perform(get("/api/auctions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(auction.getId().toString()))
            .andExpect(jsonPath("$[0].listingId").value(listing.getId().toString()))
            .andExpect(jsonPath("$[0].title").value("Gaming Phone"))
            .andExpect(jsonPath("$[0].status").value("ACTIVE"))
            .andExpect(jsonPath("$[0].totalBids").value(1))
            .andExpect(jsonPath("$[0].nextMinimumBid").value(1260.00));
    }

    @Test
    void getAuctionDetailAndBidHistoryShouldMatchFrontendContract() throws Exception {
        User seller = createUser("seller@example.com", Role.SELLER);
        User buyer = createUser("buyer@example.com", Role.BUYER);
        Listing listing = saveListing(seller, "Mirrorless Camera", "Travel camera", "2500.00");
        Auction auction = saveAuction(listing, AuctionStatus.ACTIVE, Instant.now().plus(3, ChronoUnit.HOURS));
        Bid bid = createBid(auction, buyer, "2600.00", 1L);

        mockMvc.perform(get("/api/auctions/{auctionId}", auction.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(auction.getId().toString()))
            .andExpect(jsonPath("$.listingId").value(listing.getId().toString()))
            .andExpect(jsonPath("$.sellerId").value(seller.getId().toString()))
            .andExpect(jsonPath("$.currentPrice").value(2600.00))
            .andExpect(jsonPath("$.leadingBid.id").value(bid.getId().toString()))
            .andExpect(jsonPath("$.bidHistory.length()").value(1))
            .andExpect(jsonPath("$.biddable").value(true));

        mockMvc.perform(get("/api/auctions/{auctionId}/bids", auction.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(bid.getId().toString()))
            .andExpect(jsonPath("$[0].bidderId").value(buyer.getId().toString()))
            .andExpect(jsonPath("$[0].amount").value(2600.00))
            .andExpect(jsonPath("$[0].winning").value(true));
    }

    private User createUser(String email, Role role) {
        return userRepository.save(User.builder()
            .email(email)
            .passwordHash(passwordEncoder.encode("password123"))
            .role(role)
            .build());
    }

    private Listing saveListing(User seller, String title, String description, String price) {
        return listingRepository.save(Listing.builder()
            .title(title)
            .description(description)
            .imageUrl("https://img.example/" + title.toLowerCase().replace(" ", "-") + ".jpg")
            .price(new BigDecimal(price))
            .category(ListingCategory.ELECTRONICS)
            .seller(seller)
            .build());
    }

    private Auction saveAuction(Listing listing, AuctionStatus status, Instant endsAt) {
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return auctionRepository.save(Auction.builder()
            .listing(listing)
            .status(status)
            .startingPrice(listing.getPrice())
            .reservePrice(listing.getPrice())
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
}
