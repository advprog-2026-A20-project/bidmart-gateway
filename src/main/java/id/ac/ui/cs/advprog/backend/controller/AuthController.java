package id.ac.ui.cs.advprog.backend.controller;

import id.ac.ui.cs.advprog.backend.dto.LoginRequest;
import id.ac.ui.cs.advprog.backend.dto.LoginResponse;
import id.ac.ui.cs.advprog.backend.dto.RegisterRequest;
import id.ac.ui.cs.advprog.backend.dto.RegisterResponse;
import id.ac.ui.cs.advprog.backend.dto.UserSummary;
import id.ac.ui.cs.advprog.backend.security.AuthenticatedUser;
import id.ac.ui.cs.advprog.backend.service.AuthService;
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

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @PreAuthorize("permitAll()")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @PreAuthorize("permitAll()")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public UserSummary me(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return authService.me(authenticatedUser.id());
    }
}
