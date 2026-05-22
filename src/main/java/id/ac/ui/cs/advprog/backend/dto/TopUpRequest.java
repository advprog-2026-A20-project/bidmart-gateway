package id.ac.ui.cs.advprog.backend.dto;

import java.math.BigDecimal;

public record TopUpRequest(
    BigDecimal amount
) {}
