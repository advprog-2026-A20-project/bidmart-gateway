package id.ac.ui.cs.advprog.backend.controller;

import id.ac.ui.cs.advprog.backend.dto.TopUpRequest;
import id.ac.ui.cs.advprog.backend.dto.TransactionResponse;
import id.ac.ui.cs.advprog.backend.dto.WalletResponse;
import id.ac.ui.cs.advprog.backend.model.Role;
import id.ac.ui.cs.advprog.backend.security.AuthenticatedUser;
import id.ac.ui.cs.advprog.backend.service.WalletServiceClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WalletControllerTest {

    private final WalletServiceClient client = mock(WalletServiceClient.class);
    private final WalletController controller = new WalletController(client);

    @Test
    void walletEndpointsShouldDelegateToServiceClient() {
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "buyer@mail.com", Role.BUYER);
        WalletResponse wallet = new WalletResponse(user.id(), new BigDecimal("100"), new BigDecimal("90"), new BigDecimal("10"), user.id());
        TransactionResponse tx = new TransactionResponse(
            UUID.randomUUID(),
            "TOP_UP",
            new BigDecimal("50"),
            new BigDecimal("100"),
            "ref-1",
            Instant.now()
        );

        when(client.getBalance(user.id())).thenReturn(wallet);
        when(client.topUp(user.id(), new TopUpRequest(new BigDecimal("50")))).thenReturn(wallet);
        when(client.getTransactions(user.id())).thenReturn(List.of(tx));

        assertEquals(wallet, controller.getBalance(user));
        assertEquals(wallet, controller.topUp(user, new TopUpRequest(new BigDecimal("50"))));
        assertEquals(1, controller.getTransactionHistory(user).size());

        verify(client).getBalance(user.id());
        verify(client).topUp(user.id(), new TopUpRequest(new BigDecimal("50")));
        verify(client).getTransactions(user.id());
    }
}
