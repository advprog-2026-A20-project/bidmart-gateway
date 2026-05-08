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
    "spring.datasource.url=jdbc:h2:mem:listingreadtest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "security.jwt.secret=test-secret-please-change-32-chars",
    "security.jwt.expiration-seconds=3600"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ListingReadIntegrationTest {

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
    void listingReadEndpointsShouldMatchFrontendContract() throws Exception {
        User seller = createUser("seller@example.com", Role.SELLER);
        User buyer = createUser("buyer@example.com", Role.BUYER);
        Listing listing = saveListing(
            seller,
            "Mirrorless Camera",
            "Travel camera",
            ListingCategory.ELECTRONICS,
            "2500.00"
        );
        Auction auction = saveAuction(listing, AuctionStatus.ACTIVE, Instant.now().plus(3, ChronoUnit.HOURS));
        createBid(auction, buyer, "2600.00", 1L);

        mockMvc.perform(get("/api/listings"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(listing.getId().toString()))
            .andExpect(jsonPath("$[0].sellerId").value(seller.getId().toString()))
            .andExpect(jsonPath("$[0].sellerEmail").value("seller@example.com"))
            .andExpect(jsonPath("$[0].auctionId").value(auction.getId().toString()))
            .andExpect(jsonPath("$[0].auctionStatus").value("ACTIVE"))
            .andExpect(jsonPath("$[0].totalBids").value(1))
            .andExpect(jsonPath("$[0].hasBids").value(true));

        mockMvc.perform(get("/api/listings/{listingId}", listing.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(listing.getId().toString()))
            .andExpect(jsonPath("$.category").value("ELECTRONICS"))
            .andExpect(jsonPath("$.categoryPath").value("Elektronik"))
            .andExpect(jsonPath("$.sellerEmail").value("seller@example.com"))
            .andExpect(jsonPath("$.auctionId").value(auction.getId().toString()))
            .andExpect(jsonPath("$.startingPrice").value(2500.00))
            .andExpect(jsonPath("$.totalBids").value(1))
            .andExpect(jsonPath("$.hasBids").value(true));
    }

    @Test
    void categoryEndpointsShouldReturnExistingContract() throws Exception {
        mockMvc.perform(get("/api/listings/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0]").value("ELECTRONICS"))
            .andExpect(jsonPath("$[1]").value("ELECTRONICS_PHONE"));

        mockMvc.perform(get("/api/listings/categories/tree"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].key").value("ELECTRONICS"))
            .andExpect(jsonPath("$[0].label").value("Elektronik"))
            .andExpect(jsonPath("$[0].path").value("Elektronik"))
            .andExpect(jsonPath("$[0].children[0].key").value("ELECTRONICS_PHONE"));
    }

    private User createUser(String email, Role role) {
        return userRepository.save(User.builder()
            .email(email)
            .passwordHash(passwordEncoder.encode("password123"))
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
