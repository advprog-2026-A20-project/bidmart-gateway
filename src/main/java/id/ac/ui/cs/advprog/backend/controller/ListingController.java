package id.ac.ui.cs.advprog.backend.controller;

import id.ac.ui.cs.advprog.backend.dto.ListingCreateRequest;
import id.ac.ui.cs.advprog.backend.dto.ListingDetailResponse;
import id.ac.ui.cs.advprog.backend.dto.ListingBidValidationResponse;
import id.ac.ui.cs.advprog.backend.dto.ListingCategoryNodeResponse;
import id.ac.ui.cs.advprog.backend.dto.ListingResponse;
import id.ac.ui.cs.advprog.backend.dto.ListingUpdateRequest;
import id.ac.ui.cs.advprog.backend.model.ListingCategory;
import id.ac.ui.cs.advprog.backend.security.AuthenticatedUser;
import id.ac.ui.cs.advprog.backend.service.ListingCommandServiceClient;
import id.ac.ui.cs.advprog.backend.service.ListingReadGateway;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/listings")
public class ListingController {

    private final ListingCommandServiceClient listingCommandServiceClient;
    private final ListingReadGateway listingReadGateway;

    public ListingController(
        ListingCommandServiceClient listingCommandServiceClient,
        ListingReadGateway listingReadGateway
    ) {
        this.listingCommandServiceClient = listingCommandServiceClient;
        this.listingReadGateway = listingReadGateway;
    }

    @PostMapping
    @PreAuthorize("hasRole('SELLER')")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public ListingResponse create(
        @Valid @RequestBody ListingCreateRequest request,
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return listingCommandServiceClient.create(request);
    }

    @GetMapping
    @PreAuthorize("permitAll()")
    public List<ListingResponse> list(
        @RequestParam(required = false) ListingCategory category,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) BigDecimal minPrice,
        @RequestParam(required = false) BigDecimal maxPrice,
        @RequestParam(required = false) Instant endingAfter,
        @RequestParam(required = false) Instant endingBefore
    ) {
        return listingReadGateway.list(
            category,
            keyword,
            minPrice,
            maxPrice,
            endingAfter,
            endingBefore
        );
    }

    @GetMapping("/{listingId}")
    @PreAuthorize("permitAll()")
    public ListingDetailResponse getById(@PathVariable UUID listingId) {
        return listingReadGateway.getById(listingId);
    }

    @GetMapping("/categories")
    @PreAuthorize("permitAll()")
    public List<ListingCategory> categories() {
        return listingReadGateway.categories();
    }

    @GetMapping("/categories/tree")
    @PreAuthorize("permitAll()")
    public List<ListingCategoryNodeResponse> categoryTree() {
        return listingReadGateway.categoryTree();
    }

    @GetMapping("/{listingId}/validation")
    @PreAuthorize("permitAll()")
    public ListingBidValidationResponse validateForBid(@PathVariable UUID listingId) {
        return listingCommandServiceClient.validateForBid(listingId);
    }

    @PutMapping("/{listingId}")
    @PreAuthorize("hasRole('SELLER')")
    public ListingDetailResponse update(
        @PathVariable UUID listingId,
        @Valid @RequestBody ListingUpdateRequest request,
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return listingCommandServiceClient.update(listingId, request);
    }

    @DeleteMapping("/{listingId}")
    @PreAuthorize("hasRole('SELLER')")
    public ListingDetailResponse cancel(
        @PathVariable UUID listingId,
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return listingCommandServiceClient.cancel(listingId);
    }
}
