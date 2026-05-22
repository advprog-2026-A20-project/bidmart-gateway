package id.ac.ui.cs.advprog.backend.controller;

import id.ac.ui.cs.advprog.backend.dto.ForgotPasswordRequest;
import id.ac.ui.cs.advprog.backend.dto.LoginRequest;
import id.ac.ui.cs.advprog.backend.dto.LoginResponse;
import id.ac.ui.cs.advprog.backend.dto.MessageResponse;
import id.ac.ui.cs.advprog.backend.dto.RegisterRequest;
import id.ac.ui.cs.advprog.backend.dto.RegisterResponse;
import id.ac.ui.cs.advprog.backend.dto.ResetPasswordRequest;
import id.ac.ui.cs.advprog.backend.dto.UserSummary;
import id.ac.ui.cs.advprog.backend.dto.VerifyResetOtpRequest;
import id.ac.ui.cs.advprog.backend.model.Role;
import id.ac.ui.cs.advprog.backend.security.AuthenticatedUser;
import id.ac.ui.cs.advprog.backend.service.AuthServiceClient;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    private static final String EMAIL = "user@mail.com";
    private static final String PASSWORD = "password123";
    private final AuthServiceClient authServiceClient = mock(AuthServiceClient.class);
    private final AuthController controller = new AuthController(authServiceClient);

    @Test
    void registerShouldDelegateToServiceClient() {
        RegisterRequest request = new RegisterRequest(EMAIL, PASSWORD, Role.BUYER);
        RegisterResponse expected = new RegisterResponse(UUID.randomUUID(), EMAIL, Role.BUYER);
        when(authServiceClient.register(request)).thenReturn(expected);

        RegisterResponse result = controller.register(request);

        assertEquals(expected, result);
        verify(authServiceClient).register(request);
    }

    @Test
    void loginShouldDelegateToServiceClient() {
        LoginRequest request = new LoginRequest(EMAIL, PASSWORD);
        UserSummary user = new UserSummary(UUID.randomUUID(), EMAIL, Role.BUYER, BigDecimal.ZERO, BigDecimal.ZERO);
        LoginResponse expected = new LoginResponse("token", user);
        when(authServiceClient.login(request)).thenReturn(expected);

        LoginResponse result = controller.login(request);

        assertEquals(expected, result);
        verify(authServiceClient).login(request);
    }

    @Test
    void meShouldDelegateToServiceClient() {
        AuthenticatedUser principal = new AuthenticatedUser(UUID.randomUUID(), EMAIL, Role.BUYER);
        UserSummary expected = new UserSummary(principal.id(), EMAIL, Role.BUYER, BigDecimal.ZERO, BigDecimal.ZERO);
        when(authServiceClient.me()).thenReturn(expected);

        UserSummary result = controller.me(principal);

        assertEquals(expected, result);
        verify(authServiceClient).me();
    }

    @Test
    void forgotPasswordShouldDelegateToServiceClient() {
        ForgotPasswordRequest request = new ForgotPasswordRequest(EMAIL);
        MessageResponse expected = new MessageResponse("If the email is registered, an OTP has been sent");
        when(authServiceClient.forgotPassword(request)).thenReturn(expected);

        MessageResponse result = controller.forgotPassword(request);

        assertEquals(expected, result);
        verify(authServiceClient).forgotPassword(request);
    }

    @Test
    void verifyResetOtpShouldDelegateToServiceClient() {
        VerifyResetOtpRequest request = new VerifyResetOtpRequest(EMAIL, "123456");
        MessageResponse expected = new MessageResponse("OTP is valid");
        when(authServiceClient.verifyResetOtp(request)).thenReturn(expected);

        MessageResponse result = controller.verifyResetOtp(request);

        assertEquals(expected, result);
        verify(authServiceClient).verifyResetOtp(request);
    }

    @Test
    void resetPasswordShouldDelegateToServiceClient() {
        ResetPasswordRequest request = new ResetPasswordRequest(EMAIL, "123456", PASSWORD);
        MessageResponse expected = new MessageResponse("Password has been reset successfully");
        when(authServiceClient.resetPassword(request)).thenReturn(expected);

        MessageResponse result = controller.resetPassword(request);

        assertEquals(expected, result);
        verify(authServiceClient).resetPassword(request);
    }
}
