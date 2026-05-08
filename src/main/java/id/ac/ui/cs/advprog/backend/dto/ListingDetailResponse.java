package id.ac.ui.cs.advprog.backend.dto;

import id.ac.ui.cs.advprog.backend.model.AuctionStatus;
import id.ac.ui.cs.advprog.backend.model.ListingCategory;
import id.ac.ui.cs.advprog.backend.model.ListingStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ListingDetailResponse(
    UUID id,
    String title,
    String description,
    String imageUrl,
    BigDecimal price,
    BigDecimal startingPrice,
    BigDecimal reservePrice,
    BigDecimal minimumBidIncrement,
    Long durationMinutes,
    ListingCategory category,
    String categoryPath,
    UUID sellerId,
    String sellerEmail,
    ListingStatus status,
    UUID auctionId,
    AuctionStatus auctionStatus,
    Instant startsAt,
    Instant endsAt,
    Instant closedAt,
    long totalBids,
    boolean hasBids,
    Instant createdAt,
    Instant updatedAt,
    Instant cancelledAt
) {
}
