package id.ac.ui.cs.advprog.backend;

import id.ac.ui.cs.advprog.backend.model.Role;
import id.ac.ui.cs.advprog.backend.model.User;
import id.ac.ui.cs.advprog.backend.model.Wallet;
import id.ac.ui.cs.advprog.backend.repository.AuctionRepository;
import id.ac.ui.cs.advprog.backend.repository.BidRepository;
import id.ac.ui.cs.advprog.backend.repository.ListingRepository;
import id.ac.ui.cs.advprog.backend.repository.UserRepository;
import id.ac.ui.cs.advprog.backend.repository.WalletRepository;
import id.ac.ui.cs.advprog.backend.repository.WalletTransactionRepository;
import id.ac.ui.cs.advprog.backend.security.JwtService;
import id.ac.ui.cs.advprog.backend.service.WalletGateway;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class WalletIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

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

    @Autowired
    private WalletGateway walletGateway;

    @BeforeEach
    void setUp() {
        bidRepository.deleteAll();
        auctionRepository.deleteAll();
        listingRepository.deleteAll();
        walletTransactionRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void walletEndpointsShouldSupportBalanceTopupAndTransactionHistory() throws Exception {
        User buyer = userRepository.save(User.builder()
            .email("buyer@example.com")
            .passwordHash(passwordEncoder.encode("password123"))
            .role(Role.BUYER)
            .build());

        walletRepository.save(Wallet.builder()
            .user(buyer)
            .balance(new BigDecimal("50000.00"))
            .build());

        mockMvc.perform(get("/api/wallet/balance")
                .header("Authorization", bearerToken(buyer)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(buyer.getId().toString()))
            .andExpect(jsonPath("$.balance").value(50000.00))
            .andExpect(jsonPath("$.availableBalance").value(50000.00))
            .andExpect(jsonPath("$.heldBalance").value(0));

        mockMvc.perform(post("/api/wallet/topup")
                .header("Authorization", bearerToken(buyer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "amount": 25000
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(buyer.getId().toString()))
            .andExpect(jsonPath("$.balance").value(75000.00))
            .andExpect(jsonPath("$.availableBalance").value(75000.00))
            .andExpect(jsonPath("$.heldBalance").value(0));

        mockMvc.perform(get("/api/wallet/transactions")
                .header("Authorization", bearerToken(buyer)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].type").value("TOPUP"))
            .andExpect(jsonPath("$[0].amount").value(25000.00))
            .andExpect(jsonPath("$[0].balanceAfter").value(75000.00));
    }

    @Test
    void walletGatewayShouldHoldReleaseAndCaptureFundsConsistently() {
        User buyer = userRepository.save(User.builder()
            .email("bidder@example.com")
            .passwordHash(passwordEncoder.encode("password123"))
            .role(Role.BUYER)
            .availableBalance(new BigDecimal("100000.00"))
            .heldBalance(BigDecimal.ZERO)
            .build());

        UUID auctionId = UUID.randomUUID();

        walletGateway.holdFunds(buyer.getId(), auctionId, new BigDecimal("40000.00"));
        User afterHold = userRepository.findById(buyer.getId()).orElseThrow();
        Wallet walletAfterHold = walletRepository.findByUserId(buyer.getId()).orElseThrow();
        assertWalletBalances(afterHold, walletAfterHold, "60000.00", "40000.00");

        walletGateway.releaseFunds(buyer.getId(), auctionId, new BigDecimal("10000.00"));
        User afterRelease = userRepository.findById(buyer.getId()).orElseThrow();
        Wallet walletAfterRelease = walletRepository.findByUserId(buyer.getId()).orElseThrow();
        assertWalletBalances(afterRelease, walletAfterRelease, "70000.00", "30000.00");

        walletGateway.captureFunds(buyer.getId(), auctionId, new BigDecimal("30000.00"));
        User afterCapture = userRepository.findById(buyer.getId()).orElseThrow();
        Wallet walletAfterCapture = walletRepository.findByUserId(buyer.getId()).orElseThrow();
        assertWalletBalances(afterCapture, walletAfterCapture, "70000.00", "0.00");

        User seller = userRepository.save(User.builder()
            .email("seller@example.com")
            .passwordHash(passwordEncoder.encode("password123"))
            .role(Role.SELLER)
            .availableBalance(BigDecimal.ZERO)
            .heldBalance(BigDecimal.ZERO)
            .build());

        walletGateway.creditFunds(seller.getId(), auctionId, new BigDecimal("30000.00"));
        User afterSellerCredit = userRepository.findById(seller.getId()).orElseThrow();
        Wallet sellerWallet = walletRepository.findByUserId(seller.getId()).orElseThrow();
        assertWalletBalances(afterSellerCredit, sellerWallet, "30000.00", "0.00");

        var transactions = walletTransactionRepository.findByWalletIdOrderByCreatedAtDesc(walletAfterCapture.getId());
        org.assertj.core.api.Assertions.assertThat(transactions)
            .extracting(transaction -> transaction.getType().name())
            .containsExactly("PAYMENT", "RELEASE", "HOLD");
    }

    private String bearerToken(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }

    private void assertWalletBalances(User user, Wallet wallet, String availableBalance, String heldBalance) {
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal(availableBalance), user.getAvailableBalance());
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal(heldBalance), user.getHeldBalance());
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal(availableBalance), wallet.getBalance());
    }
}
