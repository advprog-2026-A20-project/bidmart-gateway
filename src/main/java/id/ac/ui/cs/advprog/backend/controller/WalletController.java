package id.ac.ui.cs.advprog.backend.controller;

import id.ac.ui.cs.advprog.backend.dto.TopUpRequest;
import id.ac.ui.cs.advprog.backend.dto.TransactionResponse;
import id.ac.ui.cs.advprog.backend.dto.WalletResponse;
import id.ac.ui.cs.advprog.backend.security.AuthenticatedUser;
import id.ac.ui.cs.advprog.backend.service.WalletService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping("/balance")
    public WalletResponse getBalance(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return walletService.getWallet(authenticatedUser.id());
    }

    @PostMapping("/topup")
    @ResponseStatus(HttpStatus.OK)
    public WalletResponse topUp(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @RequestBody TopUpRequest request
    ) {
        return walletService.topUp(authenticatedUser.id(), request);
    }

    @GetMapping("/transactions")
    public List<TransactionResponse> getTransactionHistory(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return walletService.getTransactionHistory(authenticatedUser.id());
    }
}
