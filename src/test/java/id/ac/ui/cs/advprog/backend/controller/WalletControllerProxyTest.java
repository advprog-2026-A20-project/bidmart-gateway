package id.ac.ui.cs.advprog.backend.controller;

import id.ac.ui.cs.advprog.backend.service.HttpProxyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WalletControllerProxyTest {

    private HttpProxyService proxyService;
    private WalletController controller;

    @BeforeEach
    void setUp() {
        proxyService = Mockito.mock(HttpProxyService.class);
        controller = new WalletController(proxyService, "http://wallet-service:8085");
    }

    @Test
    void topUpForwardsToWalletService() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String body = "{\"amount\":50000}";
        when(proxyService.forward(HttpMethod.POST, "http://wallet-service:8085", "/wallet/topup", request, body))
            .thenReturn(ResponseEntity.ok("ok"));

        ResponseEntity<String> response = controller.topUp(request, body);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(proxyService).forward(HttpMethod.POST, "http://wallet-service:8085", "/wallet/topup", request, body);
    }
}
