package id.ac.ui.cs.advprog.backend.controller;

import id.ac.ui.cs.advprog.backend.service.HttpProxyService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerProxyTest {

    private HttpProxyService proxyService;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        proxyService = Mockito.mock(HttpProxyService.class);
        controller = new AuthController(proxyService, "http://auth-service:8081");
    }

    @Test
    void registerForwardsToAuthService() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String body = "{\"email\":\"a@b.com\",\"password\":\"password123\",\"role\":\"BUYER\"}";
        when(proxyService.forward(HttpMethod.POST, "http://auth-service:8081", "/auth/register", request, body))
            .thenReturn(ResponseEntity.status(HttpStatus.CREATED).body("ok"));

        ResponseEntity<String> response = controller.register(request, body);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(proxyService).forward(HttpMethod.POST, "http://auth-service:8081", "/auth/register", request, body);
    }

    @Test
    void meForwardsToAuthServiceWithoutBody() {
        HttpServletRequest request = new MockHttpServletRequest();
        when(proxyService.forward(HttpMethod.GET, "http://auth-service:8081", "/auth/me", request, null))
            .thenReturn(ResponseEntity.ok("{}"));

        ResponseEntity<String> response = controller.me(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(proxyService).forward(HttpMethod.GET, "http://auth-service:8081", "/auth/me", request, null);
    }
}
