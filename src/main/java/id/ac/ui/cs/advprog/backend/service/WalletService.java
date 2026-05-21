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
import org.springframework.transaction.annotation.Transactional;
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

    @Transactional
    public WalletResponse getWallet(UUID userId) {
        User user = loadUserForUpdate(userId);
        Wallet wallet = ensureWallet(user);
        return toResponse(wallet, user);
    }

    @Transactional
    public WalletResponse topUp(UUID userId, TopUpRequest request) {
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be greater than 0");
        }

        User user = loadUserForUpdate(userId);
        user.setAvailableBalance(defaultAmount(user.getAvailableBalance()).add(request.amount()));
        userRepository.save(user);

        Wallet wallet = syncWalletBalance(user);
        recordTransaction(
            wallet,
            WalletTransaction.TransactionType.TOPUP,
            request.amount(),
            user.getAvailableBalance(),
            "Top up saldo"
        );

        return toResponse(wallet, user);
    }

    @Transactional
    public List<TransactionResponse> getTransactionHistory(UUID userId) {
        User user = loadUserForUpdate(userId);
        Wallet wallet = ensureWallet(user);

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

    @Transactional
    public void createWalletForUser(User user) {
        syncWalletBalance(user);
    }

    private User loadUserForUpdate(UUID userId) {
        return userRepository.findByIdForUpdate(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private Wallet ensureWallet(User user) {
        Wallet wallet = walletRepository.findByUserId(user.getId())
            .orElseGet(() -> Wallet.builder()
                .user(user)
                .balance(defaultAmount(user.getAvailableBalance()))
                .build());

        reconcileLegacyWalletBalance(user, wallet);
        return syncWalletBalance(user, wallet);
    }

    private Wallet syncWalletBalance(User user) {
        Wallet wallet = walletRepository.findByUserId(user.getId())
            .orElseGet(() -> Wallet.builder()
                .user(user)
                .balance(defaultAmount(user.getAvailableBalance()))
                .build());
        return syncWalletBalance(user, wallet);
    }

    private Wallet syncWalletBalance(User user, Wallet wallet) {
        wallet.setBalance(defaultAmount(user.getAvailableBalance()));
        wallet.setUpdatedAt(Instant.now());
        return walletRepository.save(wallet);
    }

    private void reconcileLegacyWalletBalance(User user, Wallet wallet) {
        BigDecimal availableBalance = defaultAmount(user.getAvailableBalance());
        BigDecimal heldBalance = defaultAmount(user.getHeldBalance());
        BigDecimal walletBalance = defaultAmount(wallet.getBalance());
        if (availableBalance.signum() == 0 && heldBalance.signum() == 0 && walletBalance.signum() > 0) {
            user.setAvailableBalance(walletBalance);
            userRepository.save(user);
        }
    }

    private WalletResponse toResponse(Wallet wallet, User user) {
        BigDecimal availableBalance = defaultAmount(user.getAvailableBalance());
        BigDecimal heldBalance = defaultAmount(user.getHeldBalance());
        return new WalletResponse(
            wallet.getId(),
            availableBalance,
            availableBalance,
            heldBalance,
            user.getId()
        );
    }

    private void recordTransaction(
        Wallet wallet,
        WalletTransaction.TransactionType type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String description
    ) {
        WalletTransaction transaction = WalletTransaction.builder()
            .wallet(wallet)
            .type(type)
            .amount(amount)
            .balanceAfter(balanceAfter)
            .description(description)
            .build();
        transactionRepository.save(transaction);
    }

    private BigDecimal defaultAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }
}
