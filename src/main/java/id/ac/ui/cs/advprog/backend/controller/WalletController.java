package id.ac.ui.cs.advprog.backend.controller;

import id.ac.ui.cs.advprog.backend.service.HttpProxyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final HttpProxyService proxyService;
    private final String walletServiceBaseUrl;

    public WalletController(
        HttpProxyService proxyService,
        @Value("${WALLET_SERVICE_BASE_URL:http://localhost:8085}") String walletServiceBaseUrl
    ) {
        this.proxyService = proxyService;
        this.walletServiceBaseUrl = walletServiceBaseUrl;
    }

    @GetMapping("/balance")
    public ResponseEntity<String> getBalance(HttpServletRequest request) {
        return proxyService.forward(HttpMethod.GET, walletServiceBaseUrl, "/wallet/balance", request, null);
    }

    @PostMapping("/topup")
    public ResponseEntity<String> topUp(HttpServletRequest request, @RequestBody(required = false) String body) {
        return proxyService.forward(HttpMethod.POST, walletServiceBaseUrl, "/wallet/topup", request, body);
    }

    @GetMapping("/transactions")
    public ResponseEntity<String> getTransactionHistory(HttpServletRequest request) {
        return proxyService.forward(HttpMethod.GET, walletServiceBaseUrl, "/wallet/transactions", request, null);
    }
}
