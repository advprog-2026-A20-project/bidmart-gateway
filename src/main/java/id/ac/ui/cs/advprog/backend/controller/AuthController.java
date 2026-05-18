package id.ac.ui.cs.advprog.backend.controller;

import id.ac.ui.cs.advprog.backend.service.HttpProxyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final HttpProxyService proxyService;
    private final String authServiceBaseUrl;

    public AuthController(
        HttpProxyService proxyService,
        @Value("${AUTH_SERVICE_BASE_URL:http://localhost:8081}") String authServiceBaseUrl
    ) {
        this.proxyService = proxyService;
        this.authServiceBaseUrl = authServiceBaseUrl;
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(
        HttpServletRequest request,
        @RequestBody(required = false) String body
    ) {
        return proxyService.forward(HttpMethod.POST, authServiceBaseUrl, "/auth/register", request, body);
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(
        HttpServletRequest request,
        @RequestBody(required = false) String body
    ) {
        return proxyService.forward(HttpMethod.POST, authServiceBaseUrl, "/auth/login", request, body);
    }

    @GetMapping("/me")
    public ResponseEntity<String> me(HttpServletRequest request) {
        return proxyService.forward(HttpMethod.GET, authServiceBaseUrl, "/auth/me", request, null);
    }
}
