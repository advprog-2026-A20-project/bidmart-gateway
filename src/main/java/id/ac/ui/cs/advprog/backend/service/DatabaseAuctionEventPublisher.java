package id.ac.ui.cs.advprog.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import id.ac.ui.cs.advprog.backend.model.Auction;
import id.ac.ui.cs.advprog.backend.model.AuctionEvent;
import id.ac.ui.cs.advprog.backend.model.Bid;
import id.ac.ui.cs.advprog.backend.repository.AuctionEventRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DatabaseAuctionEventPublisher implements AuctionEventPublisher {

    private final AuctionEventRepository auctionEventRepository;
    private final ObjectMapper objectMapper;

    public DatabaseAuctionEventPublisher(
        AuctionEventRepository auctionEventRepository,
        ObjectMapper objectMapper
    ) {
        this.auctionEventRepository = auctionEventRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishAuctionActivated(Auction auction) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("auctionId", auction.getId());
        payload.put("listingId", auction.getListing().getId());
        payload.put("status", auction.getStatus().name());
        payload.put("startsAt", auction.getStartsAt());
        payload.put("endsAt", auction.getEndsAt());
        persistEvent(auction, "AuctionActivated", payload);
    }

    @Override
    public void publishBidPlaced(Auction auction, Bid bid, Bid previousLeadingBid) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("auctionId", auction.getId());
        payload.put("listingId", auction.getListing().getId());
        payload.put("bidId", bid.getId());
        payload.put("bidderId", bid.getBidder().getId());
        payload.put("amount", bid.getAmount());
        payload.put("sequenceNumber", bid.getSequenceNumber());
        payload.put("submittedAt", bid.getSubmittedAt());
        payload.put("previousLeaderId", previousLeadingBid == null ? null : previousLeadingBid.getBidder().getId());
        payload.put("previousLeaderBidId", previousLeadingBid == null ? null : previousLeadingBid.getId());
        persistEvent(auction, "BidPlaced", payload);
    }

    @Override
    public void publishAuctionResolved(Auction auction, Bid winningBid, boolean reserveMet) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("auctionId", auction.getId());
        payload.put("listingId", auction.getListing().getId());
        payload.put("status", auction.getStatus().name());
        payload.put("reserveMet", reserveMet);
        payload.put("closedAt", auction.getClosedAt());
        payload.put("winningBidId", winningBid == null ? null : winningBid.getId());
        payload.put("winnerId", winningBid == null ? null : winningBid.getBidder().getId());
        payload.put("winningAmount", winningBid == null ? null : winningBid.getAmount());
        String eventType = reserveMet ? "WinnerDetermined" : "AuctionUnsold";
        persistEvent(auction, eventType, payload);
    }

    private void persistEvent(Auction auction, String eventType, Map<String, Object> payload) {
        try {
            auctionEventRepository.save(AuctionEvent.builder()
                .aggregateId(auction.getId())
                .eventType(eventType)
                .payload(objectMapper.writeValueAsString(payload))
                .build());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize auction event payload", exception);
        }
    }
}

