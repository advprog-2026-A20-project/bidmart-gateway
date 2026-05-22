package id.ac.ui.cs.advprog.backend.service;

import id.ac.ui.cs.advprog.backend.dto.TopUpRequest;
import id.ac.ui.cs.advprog.backend.dto.TransactionResponse;
import id.ac.ui.cs.advprog.backend.dto.WalletResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class WalletServiceClientTest {

    @Test
    void getBalanceShouldMapServiceResponse() {
        WalletServiceClient client = new WalletServiceClient(true, "http://wallet-service/");
        MockRestServiceServer server = serverFor(client);

        UUID userId = UUID.randomUUID();
        server.expect(requestTo("http://wallet-service/wallets/" + userId + "/balance"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"userId\":\"" + userId + "\",\"availableBalance\":1000.00,\"heldBalance\":150.00}"));

        WalletResponse response = client.getBalance(userId);

        assertEquals(userId, response.userId());
        assertEquals(new BigDecimal("1000.00"), response.balance());
        assertEquals(new BigDecimal("1000.00"), response.availableBalance());
        assertEquals(new BigDecimal("150.00"), response.heldBalance());
        server.verify();
    }

    @Test
    void topUpShouldCallServiceAndReturnMappedWallet() {
        WalletServiceClient client = new WalletServiceClient(true, "http://wallet-service");
        MockRestServiceServer server = serverFor(client);

        UUID userId = UUID.randomUUID();
        server.expect(requestTo("http://wallet-service/wallets/" + userId + "/top-up"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"userId\":\"" + userId + "\",\"availableBalance\":1300.00,\"heldBalance\":150.00}"));

        WalletResponse response = client.topUp(userId, new TopUpRequest(new BigDecimal("300.00")));

        assertEquals(new BigDecimal("1300.00"), response.availableBalance());
        server.verify();
    }

    @Test
    void getTransactionsShouldReturnEmptyWhenBodyIsNull() {
        WalletServiceClient client = new WalletServiceClient(true, "http://wallet-service");
        MockRestServiceServer server = serverFor(client);

        UUID userId = UUID.randomUUID();
        server.expect(requestTo("http://wallet-service/wallets/" + userId + "/transactions"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.OK));

        List<TransactionResponse> responses = client.getTransactions(userId);

        assertEquals(0, responses.size());
        server.verify();
    }

    @Test
    void getTransactionsShouldMapArrayResponse() {
        WalletServiceClient client = new WalletServiceClient(true, "http://wallet-service");
        MockRestServiceServer server = serverFor(client);

        UUID userId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        server.expect(requestTo("http://wallet-service/wallets/" + userId + "/transactions"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                    [{
                      "transactionId":"%s",
                      "userId":"%s",
                      "type":"TOP_UP",
                      "amount":200.00,
                      "reference":"ref-123",
                      "availableBalanceAfter":1200.00,
                      "heldBalanceAfter":100.00,
                      "timestamp":"2026-01-01T00:00:00Z"
                    }]
                    """.formatted(transactionId, userId)));

        List<TransactionResponse> responses = client.getTransactions(userId);

        assertEquals(1, responses.size());
        assertEquals(transactionId, responses.get(0).id());
        assertEquals("TOP_UP", responses.get(0).type());
        server.verify();
    }

    @Test
    void disabledClientShouldThrowServiceUnavailable() {
        WalletServiceClient client = new WalletServiceClient(false, "http://wallet-service");

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> client.getBalance(UUID.randomUUID())
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
        assertEquals("Wallet service base URL is not configured", exception.getReason());
    }

    @Test
    void topUpShouldMapErrorMessageFromErrorBody() {
        WalletServiceClient client = new WalletServiceClient(true, "http://wallet-service");
        MockRestServiceServer server = serverFor(client);

        UUID userId = UUID.randomUUID();
        server.expect(requestTo("http://wallet-service/wallets/" + userId + "/top-up"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"message\":\"Amount must be positive\"}"));

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> client.topUp(userId, new TopUpRequest(new BigDecimal("0")))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Amount must be positive", exception.getReason());
        server.verify();
    }

    private MockRestServiceServer serverFor(WalletServiceClient client) {
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        return MockRestServiceServer.bindTo(restTemplate).build();
    }
}
