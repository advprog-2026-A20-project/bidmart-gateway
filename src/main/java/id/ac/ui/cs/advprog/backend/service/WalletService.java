package id.ac.ui.cs.advprog.backend.service;

import id.ac.ui.cs.advprog.backend.dto.TopUpRequest;
import id.ac.ui.cs.advprog.backend.dto.TransactionResponse;
import id.ac.ui.cs.advprog.backend.dto.WalletResponse;
import id.ac.ui.cs.advprog.backend.model.Wallet;
import id.ac.ui.cs.advprog.backend.model.WalletTransaction;
import id.ac.ui.cs.advprog.backend.model.User;
import id.ac.ui.cs.advprog.backend.repository.WalletRepository;
import id.ac.ui.cs.advprog.backend.repository.WalletTransactionRepository;
import id.ac.ui.cs.advprog.backend.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public WalletService(
        WalletRepository walletRepository,
        WalletTransactionRepository transactionRepository,
        UserRepository userRepository
    ) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    public WalletResponse getWallet(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Wallet wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found"));

        return new WalletResponse(wallet.getId(), wallet.getBalance(), wallet.getUser().getId());
    }

    public WalletResponse topUp(UUID userId, TopUpRequest request) {
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be greater than 0");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Wallet wallet = walletRepository.findByUserId(userId)
            .orElse(null);

        if (wallet == null) {
            wallet = Wallet.builder()
                .user(user)
                .balance(request.amount())
                .build();
            wallet = walletRepository.save(wallet);
        } else {
            wallet.setBalance(wallet.getBalance().add(request.amount()));
            wallet.setUpdatedAt(Instant.now());
            wallet = walletRepository.save(wallet);
        }

        // Record transaction
        WalletTransaction transaction = WalletTransaction.builder()
            .wallet(wallet)
            .type(WalletTransaction.TransactionType.TOPUP)
            .amount(request.amount())
            .balanceAfter(wallet.getBalance())
            .description("Top up saldo")
            .build();
        transactionRepository.save(transaction);

        return new WalletResponse(wallet.getId(), wallet.getBalance(), wallet.getUser().getId());
    }

    public List<TransactionResponse> getTransactionHistory(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Wallet wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found"));

        List<WalletTransaction> transactions = transactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId());

        return transactions.stream()
            .map(t -> new TransactionResponse(
                t.getId(),
                t.getType().toString(),
                t.getAmount(),
                t.getBalanceAfter(),
                t.getDescription(),
                t.getCreatedAt()
            ))
            .toList();
    }

    public void createWalletForUser(User user) {
        var existingWallet = walletRepository.findByUserId(user.getId());
        if (existingWallet.isEmpty()) {
            Wallet wallet = Wallet.builder()
                .user(user)
                .balance(BigDecimal.ZERO)
                .build();
            walletRepository.save(wallet);
        }
    }
}
