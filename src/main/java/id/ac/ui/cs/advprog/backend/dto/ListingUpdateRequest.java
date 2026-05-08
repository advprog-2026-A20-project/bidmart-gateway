package id.ac.ui.cs.advprog.backend.dto;

import id.ac.ui.cs.advprog.backend.model.ListingCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ListingUpdateRequest(
    @NotBlank(message = "Description is required")
    @Size(max = 2000, message = "Description must be at most 2000 characters")
    String description,
    @Size(max = 1000, message = "Image URL must be at most 1000 characters")
    String imageUrl,
    ListingCategory category
) {
}
