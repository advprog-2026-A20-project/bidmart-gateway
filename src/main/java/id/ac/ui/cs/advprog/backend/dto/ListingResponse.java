package id.ac.ui.cs.advprog.backend.dto;

import id.ac.ui.cs.advprog.backend.model.ListingCategory;
import id.ac.ui.cs.advprog.backend.model.ListingStatus;
import id.ac.ui.cs.advprog.backend.model.AuctionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ListingResponse(
    UUID id,
    String title,
    String description,
    String imageUrl,
    BigDecimal price,
    ListingCategory category,
    String categoryPath,
    UUID sellerId,
    String sellerEmail,
    ListingStatus status,
    UUID auctionId,
    AuctionStatus auctionStatus,
    Instant endsAt,
    long totalBids,
    boolean hasBids,
    Instant createdAt,
    Instant updatedAt,
    Instant cancelledAt
) {
}
