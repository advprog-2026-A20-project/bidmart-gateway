package id.ac.ui.cs.advprog.backend.dto;

public record LoginResponse(
    String accessToken,
    UserSummary user
) {
}
