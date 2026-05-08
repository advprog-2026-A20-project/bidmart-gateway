package id.ac.ui.cs.advprog.backend.service;

import id.ac.ui.cs.advprog.backend.dto.AuctionDetailResponse;
import id.ac.ui.cs.advprog.backend.dto.AuctionSummaryResponse;
import id.ac.ui.cs.advprog.backend.dto.BidResponse;
import java.util.List;
import java.util.UUID;

public interface AuctionReadGateway {

    List<AuctionSummaryResponse> listAuctions();

    AuctionDetailResponse getAuctionDetail(UUID auctionId);

    List<BidResponse> getBidHistory(UUID auctionId);
}
