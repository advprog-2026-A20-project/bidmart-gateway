package id.ac.ui.cs.advprog.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
    UUID id,
    String type,
    BigDecimal amount,
    BigDecimal balanceAfter,
    String description,
    Instant createdAt
) {}
