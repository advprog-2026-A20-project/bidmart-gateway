package id.ac.ui.cs.advprog.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerifyResetOtpRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Email format is invalid")
    String email,
    @NotBlank(message = "OTP is required")
    @Size(min = 6, max = 6, message = "OTP must be 6 digits")
    @Pattern(regexp = "\\d{6}", message = "OTP must contain digits only")
    String otp
) {
}
