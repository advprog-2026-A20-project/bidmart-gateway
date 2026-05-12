package id.ac.ui.cs.advprog.backend.service;

import id.ac.ui.cs.advprog.backend.dto.ListingCategoryNodeResponse;
import id.ac.ui.cs.advprog.backend.dto.ListingDetailResponse;
import id.ac.ui.cs.advprog.backend.dto.ListingResponse;
import id.ac.ui.cs.advprog.backend.model.ListingCategory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

public interface ListingReadGateway {

    List<ListingResponse> list(
        Pageable pageable,
        ListingCategory category,
        String keyword,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Instant endingAfter,
        Instant endingBefore
    );

    ListingDetailResponse getById(UUID listingId);

    List<ListingCategory> categories();

    List<ListingCategoryNodeResponse> categoryTree();
}
