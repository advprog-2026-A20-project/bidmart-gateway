package id.ac.ui.cs.advprog.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
    "security.jwt.secret=test-secret-please-change-32-chars",
    "security.jwt.expiration-seconds=3600",
    "AUTH_SERVICE_ENABLED=true",
    "AUTH_SERVICE_BASE_URL=http://localhost:8083",
    "WALLET_SERVICE_ENABLED=true",
    "WALLET_SERVICE_BASE_URL=http://localhost:8084",
    "BIDDING_COMMAND_SERVICE_ENABLED=true",
    "BIDDING_COMMAND_SERVICE_BASE_URL=http://localhost:8085",
    "LISTING_QUERY_SERVICE_ENABLED=true",
    "LISTING_QUERY_SERVICE_BASE_URL=http://localhost:8082",
    "AUCTION_QUERY_SERVICE_ENABLED=true",
    "AUCTION_QUERY_SERVICE_BASE_URL=http://localhost:8081"
})
@ActiveProfiles("test")
class BackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
