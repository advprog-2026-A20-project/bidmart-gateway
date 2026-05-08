package id.ac.ui.cs.advprog.backend.service;

import id.ac.ui.cs.advprog.backend.dto.LoginRequest;
import id.ac.ui.cs.advprog.backend.dto.LoginResponse;
import id.ac.ui.cs.advprog.backend.dto.RegisterRequest;
import id.ac.ui.cs.advprog.backend.dto.RegisterResponse;
import id.ac.ui.cs.advprog.backend.dto.UserSummary;
import id.ac.ui.cs.advprog.backend.service.WalletService;
import id.ac.ui.cs.advprog.backend.model.Role;
import id.ac.ui.cs.advprog.backend.model.User;
import id.ac.ui.cs.advprog.backend.repository.UserRepository;
import id.ac.ui.cs.advprog.backend.security.JwtService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final BigDecimal DEFAULT_BUYER_STARTING_BALANCE = new BigDecimal("1000000.00");
    private final WalletService walletService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;
    

    public AuthService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        Clock clock,
        WalletService walletService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
        this.walletService = walletService;
    }

    public RegisterResponse register(RegisterRequest request) {
        ensureRequestPresent(request, "Register request is required");
        String normalizedEmail = validateAndNormalizeEmail(request.email());
        String password = validatePassword(request.password());
        Role role = validateRegistrableRole(request.role());
        ensureEmailNotRegistered(normalizedEmail);

        User savedUser = userRepository.save(buildNewUser(normalizedEmail, password, role));
        walletService.createWalletForUser(savedUser);
        return new RegisterResponse(savedUser.getId(), savedUser.getEmail(), savedUser.getRole());
    }

    public LoginResponse login(LoginRequest request) {
        ensureRequestPresent(request, "Login request is required");
        String normalizedEmail = validateAndNormalizeEmail(request.email());
        String password = validatePassword(request.password());
        User user = loadUserByEmail(normalizedEmail);
        ensurePasswordMatches(password, user);

        String token = jwtService.generateToken(user);
        UserSummary summary = toUserSummary(user);
        return new LoginResponse(token, summary);
    }

    public UserSummary me(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        return toUserSummary(user);
    }

    private UserSummary toUserSummary(User user) {
        return new UserSummary(
            user.getId(),
            user.getEmail(),
            user.getRole(),
            user.getAvailableBalance(),
            user.getHeldBalance()
        );
    }

    private String validateAndNormalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email format is invalid");
        }
        return normalizedEmail;
    }

    private String validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required");
        }
        if (password.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters");
        }
        return password;
    }

    private void ensureEmailNotRegistered(String email) {
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
    }

    private Role validateRegistrableRole(Role role) {
        if (role == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role is required");
        }
        if (role != Role.BUYER && role != Role.SELLER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role must be BUYER or SELLER");
        }
        return role;
    }

    private void ensureRequestPresent(Object request, String errorMessage) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
        }
    }

    private User buildNewUser(String email, String rawPassword, Role role) {
        return User.builder()
            .email(email)
            .passwordHash(passwordEncoder.encode(rawPassword))
            .role(role)
            .availableBalance(role == Role.BUYER ? DEFAULT_BUYER_STARTING_BALANCE : BigDecimal.ZERO)
            .heldBalance(BigDecimal.ZERO)
            .createdAt(Instant.now(clock))
            .build();
    }

    private User loadUserByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
    }

    private void ensurePasswordMatches(String rawPassword, User user) {
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
    }
}