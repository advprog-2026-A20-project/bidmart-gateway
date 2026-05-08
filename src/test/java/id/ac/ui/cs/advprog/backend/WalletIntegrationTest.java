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
import java.math.BigDecimal;
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
            .andExpect(jsonPath("$.balance").value(50000.00));

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
            .andExpect(jsonPath("$.balance").value(75000.00));

        mockMvc.perform(get("/api/wallet/transactions")
                .header("Authorization", bearerToken(buyer)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].type").value("TOPUP"))
            .andExpect(jsonPath("$[0].amount").value(25000.00))
            .andExpect(jsonPath("$[0].balanceAfter").value(75000.00));
    }

    private String bearerToken(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }
}
