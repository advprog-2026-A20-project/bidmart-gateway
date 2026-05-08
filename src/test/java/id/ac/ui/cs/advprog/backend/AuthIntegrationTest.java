package id.ac.ui.cs.advprog.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import id.ac.ui.cs.advprog.backend.model.Role;
import id.ac.ui.cs.advprog.backend.model.User;
import id.ac.ui.cs.advprog.backend.repository.AuctionRepository;
import id.ac.ui.cs.advprog.backend.repository.BidRepository;
import id.ac.ui.cs.advprog.backend.repository.ListingRepository;
import id.ac.ui.cs.advprog.backend.repository.UserRepository;
import id.ac.ui.cs.advprog.backend.repository.WalletRepository;
import id.ac.ui.cs.advprog.backend.repository.WalletTransactionRepository;
import id.ac.ui.cs.advprog.backend.security.JwtService;
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
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ListingRepository listingRepository;

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private BidRepository bidRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @Autowired
    private WalletRepository walletRepository;

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
    void registerShouldCreateUser() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "seller@example.com",
                      "password": "password123",
                      "role": "SELLER"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value("seller@example.com"))
            .andExpect(jsonPath("$.role").value("SELLER"));
    }

    @Test
    void registerShouldRejectInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "",
                      "password": "short"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Request validation failed"))
            .andExpect(jsonPath("$.fieldErrors.email").value("Email is required"))
            .andExpect(jsonPath("$.fieldErrors.password").value("Password must be at least 8 characters"))
            .andExpect(jsonPath("$.fieldErrors.role").value("Role is required"));
    }

    @Test
    void registerShouldRejectDuplicateEmailIgnoringCase() throws Exception {
        createUser("seller@example.com", "password123", Role.SELLER);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "SELLER@example.com",
                      "password": "password123",
                      "role": "SELLER"
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Email already registered"));
    }

    @Test
    void loginShouldAcceptEmailWithDifferentCase() throws Exception {
        createUser("buyer@example.com", "password123", Role.BUYER);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "BUYER@EXAMPLE.COM",
                      "password": "password123"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.email").value("buyer@example.com"))
            .andExpect(jsonPath("$.accessToken").isString());
    }

    @Test
    void loginShouldRejectInvalidCredentials() throws Exception {
        User buyer = createUser("buyer@example.com", "password123", Role.BUYER);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new LoginPayload(buyer.getEmail(), "wrong-password")
                )))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    void meShouldReturnAuthenticatedUser() throws Exception {
        User buyer = createUser("buyer@example.com", "password123", Role.BUYER);

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", bearerToken(buyer)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(buyer.getId().toString()))
            .andExpect(jsonPath("$.email").value("buyer@example.com"))
            .andExpect(jsonPath("$.role").value("BUYER"));
    }

    @Test
    void meShouldRejectUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    @Test
    void meShouldRejectInvalidToken() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer invalid-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    private User createUser(String email, String rawPassword, Role role) {
        return userRepository.save(User.builder()
            .email(email)
            .passwordHash(passwordEncoder.encode(rawPassword))
            .role(role)
            .build());
    }

    private String bearerToken(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }

    private record LoginPayload(String email, String password) {
    }
}
