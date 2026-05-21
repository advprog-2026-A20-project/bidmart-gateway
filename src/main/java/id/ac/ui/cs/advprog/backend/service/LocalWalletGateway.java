package id.ac.ui.cs.advprog.backend.service;

import id.ac.ui.cs.advprog.backend.model.User;
import id.ac.ui.cs.advprog.backend.model.Wallet;
import id.ac.ui.cs.advprog.backend.model.WalletTransaction;
import id.ac.ui.cs.advprog.backend.repository.UserRepository;
import id.ac.ui.cs.advprog.backend.repository.WalletRepository;
import id.ac.ui.cs.advprog.backend.repository.WalletTransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LocalWalletGateway implements WalletGateway {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;

    public LocalWalletGateway(
        UserRepository userRepository,
        WalletRepository walletRepository,
        WalletTransactionRepository transactionRepository
    ) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    @Transactional
    public void holdFunds(UUID userId, UUID auctionId, BigDecimal amount) {
        BigDecimal operationAmount = sanitizeAmount(amount);
        if (isNoop(operationAmount)) {
            return;
        }

        User user = loadUserForUpdate(userId);
        applyHold(user, operationAmount);
        persistWithTransaction(
            user,
            WalletTransaction.TransactionType.HOLD,
            operationAmount,
            "Hold dana untuk auction " + auctionId
        );
    }

    @Override
    @Transactional
    public void releaseFunds(UUID userId, UUID auctionId, BigDecimal amount) {
        BigDecimal operationAmount = sanitizeAmount(amount);
        if (isNoop(operationAmount)) {
            return;
        }

        User user = loadUserForUpdate(userId);
        applyRelease(user, operationAmount);
        persistWithTransaction(
            user,
            WalletTransaction.TransactionType.RELEASE,
            operationAmount,
            "Release hold dana untuk auction " + auctionId
        );
    }

    @Override
    @Transactional
    public void captureFunds(UUID userId, UUID auctionId, BigDecimal amount) {
        BigDecimal operationAmount = sanitizeAmount(amount);
        if (isNoop(operationAmount)) {
            return;
        }

        User user = loadUserForUpdate(userId);
        applyCapture(user, operationAmount);
        persistWithTransaction(
            user,
            WalletTransaction.TransactionType.PAYMENT,
            operationAmount,
            "Pembayaran pemenang auction " + auctionId
        );
    }

    @Override
    @Transactional
    public void creditFunds(UUID userId, UUID auctionId, BigDecimal amount) {
        BigDecimal operationAmount = sanitizeAmount(amount);
        if (isNoop(operationAmount)) {
            return;
        }

        User user = loadUserForUpdate(userId);
        user.setAvailableBalance(defaultAmount(user.getAvailableBalance()).add(operationAmount));
        persistWithTransaction(
            user,
            WalletTransaction.TransactionType.PAYMENT,
            operationAmount,
            "Pembayaran diterima dari auction " + auctionId
        );
    }

    private User loadUserForUpdate(UUID userId) {
        return userRepository.findByIdForUpdate(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private BigDecimal defaultAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private void applyHold(User user, BigDecimal amount) {
        BigDecimal availableBalance = defaultAmount(user.getAvailableBalance());
        if (availableBalance.compareTo(amount) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient balance for this bid");
        }
        user.setAvailableBalance(availableBalance.subtract(amount));
        user.setHeldBalance(defaultAmount(user.getHeldBalance()).add(amount));
    }

    private void applyRelease(User user, BigDecimal amount) {
        BigDecimal heldBalance = defaultAmount(user.getHeldBalance());
        ensureHeldBalanceSufficient(heldBalance, amount, "Held balance is smaller than the released amount");
        user.setHeldBalance(heldBalance.subtract(amount));
        user.setAvailableBalance(defaultAmount(user.getAvailableBalance()).add(amount));
    }

    private void applyCapture(User user, BigDecimal amount) {
        BigDecimal heldBalance = defaultAmount(user.getHeldBalance());
        ensureHeldBalanceSufficient(heldBalance, amount, "Held balance is smaller than the captured amount");
        user.setHeldBalance(heldBalance.subtract(amount));
    }

    private void ensureHeldBalanceSufficient(BigDecimal heldBalance, BigDecimal amount, String errorMessage) {
        if (heldBalance.compareTo(amount) < 0) {
            throw new IllegalStateException(errorMessage);
        }
    }

    private void persistWithTransaction(
        User user,
        WalletTransaction.TransactionType type,
        BigDecimal amount,
        String description
    ) {
        userRepository.save(user);
        Wallet wallet = syncWalletBalance(user);
        WalletTransaction transaction = WalletTransaction.builder()
            .wallet(wallet)
            .type(type)
            .amount(amount)
            .balanceAfter(defaultAmount(user.getAvailableBalance()))
            .description(description)
            .build();
        transactionRepository.save(transaction);
    }

    private Wallet syncWalletBalance(User user) {
        Wallet wallet = walletRepository.findByUserId(user.getId())
            .orElseGet(() -> Wallet.builder()
                .user(user)
                .balance(defaultAmount(user.getAvailableBalance()))
                .build());
        wallet.setBalance(defaultAmount(user.getAvailableBalance()));
        wallet.setUpdatedAt(Instant.now());
        return walletRepository.save(wallet);
    }

    private boolean isNoop(BigDecimal amount) {
        return amount.signum() == 0;
    }

    private BigDecimal sanitizeAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return amount;
    }
}
