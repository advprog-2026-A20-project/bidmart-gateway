package id.ac.ui.cs.advprog.backend.controller;

import id.ac.ui.cs.advprog.backend.dto.PublicSellerProfileResponse;
import id.ac.ui.cs.advprog.backend.service.ListingCommandServiceClient;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class PublicUserController {

    private final ListingCommandServiceClient listingCommandServiceClient;

    public PublicUserController(ListingCommandServiceClient listingCommandServiceClient) {
        this.listingCommandServiceClient = listingCommandServiceClient;
    }

    @GetMapping("/{userId}/public-profile")
    @PreAuthorize("permitAll()")
    public PublicSellerProfileResponse publicProfile(@PathVariable UUID userId) {
        return listingCommandServiceClient.publicProfile(userId);
    }
}
