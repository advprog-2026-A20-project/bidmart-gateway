package id.ac.ui.cs.advprog.backend.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record WalletResponse(
    UUID id,
    BigDecimal balance,
    UUID userId
) {}
