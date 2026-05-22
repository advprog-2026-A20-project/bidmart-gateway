package id.ac.ui.cs.advprog.backend.controller;

import id.ac.ui.cs.advprog.backend.dto.LoginRequest;
import id.ac.ui.cs.advprog.backend.dto.LoginResponse;
import id.ac.ui.cs.advprog.backend.dto.MessageResponse;
import id.ac.ui.cs.advprog.backend.dto.ForgotPasswordRequest;
import id.ac.ui.cs.advprog.backend.dto.ResetPasswordRequest;
import id.ac.ui.cs.advprog.backend.dto.RegisterRequest;
import id.ac.ui.cs.advprog.backend.dto.RegisterResponse;
import id.ac.ui.cs.advprog.backend.dto.UserSummary;
import id.ac.ui.cs.advprog.backend.dto.VerifyResetOtpRequest;
import id.ac.ui.cs.advprog.backend.security.AuthenticatedUser;
import id.ac.ui.cs.advprog.backend.service.AuthServiceClient;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthServiceClient authServiceClient;

    public AuthController(AuthServiceClient authServiceClient) {
        this.authServiceClient = authServiceClient;
    }

    @PostMapping("/register")
    @PreAuthorize("permitAll()")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return authServiceClient.register(request);
    }

    @PostMapping("/login")
    @PreAuthorize("permitAll()")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authServiceClient.login(request);
    }

    @PostMapping("/forgot-password")
    @PreAuthorize("permitAll()")
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return authServiceClient.forgotPassword(request);
    }

    @PostMapping("/verify-reset-otp")
    @PreAuthorize("permitAll()")
    public MessageResponse verifyResetOtp(@Valid @RequestBody VerifyResetOtpRequest request) {
        return authServiceClient.verifyResetOtp(request);
    }

    @PostMapping("/reset-password")
    @PreAuthorize("permitAll()")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return authServiceClient.resetPassword(request);
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public UserSummary me(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return authServiceClient.me();
    }
}
