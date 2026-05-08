package id.ac.ui.cs.advprog.backend.service;

import id.ac.ui.cs.advprog.backend.model.User;
import id.ac.ui.cs.advprog.backend.repository.UserRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LocalWalletGateway implements WalletGateway {

    private final UserRepository userRepository;

    public LocalWalletGateway(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public void holdFunds(UUID userId, UUID auctionId, BigDecimal amount) {
        BigDecimal operationAmount = sanitizeAmount(amount);
        if (isNoop(operationAmount)) {
            return;
        }

        User user = loadUser(userId);
        applyHold(user, operationAmount);
        persist(user);
    }

    @Override
    @Transactional
    public void releaseFunds(UUID userId, UUID auctionId, BigDecimal amount) {
        BigDecimal operationAmount = sanitizeAmount(amount);
        if (isNoop(operationAmount)) {
            return;
        }

        User user = loadUser(userId);
        applyRelease(user, operationAmount);
        persist(user);
    }

    @Override
    @Transactional
    public void captureFunds(UUID userId, UUID auctionId, BigDecimal amount) {
        BigDecimal operationAmount = sanitizeAmount(amount);
        if (isNoop(operationAmount)) {
            return;
        }

        User user = loadUser(userId);
        applyCapture(user, operationAmount);
        persist(user);
    }

    private User loadUser(UUID userId) {
        return userRepository.findById(userId)
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

    private void persist(User user) {
        userRepository.save(user);
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
