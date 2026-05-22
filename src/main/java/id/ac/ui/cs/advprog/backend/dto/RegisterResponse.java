package id.ac.ui.cs.advprog.backend.dto;

import id.ac.ui.cs.advprog.backend.model.Role;
import java.util.UUID;

public record RegisterResponse(
    UUID id,
    String email,
    Role role
) {
}
