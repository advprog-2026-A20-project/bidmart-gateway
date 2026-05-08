package id.ac.ui.cs.advprog.backend.repository;

import id.ac.ui.cs.advprog.backend.model.WalletTransaction;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {
    List<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(UUID walletId);
}
