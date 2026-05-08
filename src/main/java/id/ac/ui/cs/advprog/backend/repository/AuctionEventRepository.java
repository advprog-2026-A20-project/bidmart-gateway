package id.ac.ui.cs.advprog.backend.repository;

import id.ac.ui.cs.advprog.backend.model.AuctionEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuctionEventRepository extends JpaRepository<AuctionEvent, UUID> {
}

