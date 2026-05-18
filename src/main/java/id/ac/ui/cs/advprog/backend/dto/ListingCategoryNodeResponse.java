package id.ac.ui.cs.advprog.backend.dto;

import id.ac.ui.cs.advprog.backend.model.ListingCategory;
import java.util.List;

public record ListingCategoryNodeResponse(
    ListingCategory key,
    String label,
    String path,
    List<ListingCategoryNodeResponse> children
) {
}
