package id.ac.ui.cs.advprog.backend.dto;

import id.ac.ui.cs.advprog.backend.model.Role;

public record AuthResponse(
    String token,
    String email,
    Role role
) {
}
