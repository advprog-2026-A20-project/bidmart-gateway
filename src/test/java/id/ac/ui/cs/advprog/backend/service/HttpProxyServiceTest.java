package id.ac.ui.cs.advprog.backend.service;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class HttpProxyServiceTest {

    @Test
    void forwardShouldProxySuccessfulResponseAndFilterHeaders() {
        RestTemplate restTemplate = new RestTemplate();
        HttpProxyService service = new HttpProxyService(restTemplate);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        HttpServletRequest request = requestWithHeaders("color=red", "Bearer abc");

        server.expect(requestTo("http://target-service/api/items?color=red"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
            .andExpect(headerDoesNotExist(HttpHeaders.HOST))
            .andRespond(withStatus(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Upstream", "ok")
                .header("transfer-encoding", "chunked")
                .body("{\"result\":\"ok\"}"));

        ResponseEntity<String> response = service.forward(
            HttpMethod.POST,
            "http://target-service",
            "/api/items",
            request,
            "{\"name\":\"sample\"}"
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("{\"result\":\"ok\"}", response.getBody());
        assertEquals("ok", response.getHeaders().getFirst("X-Upstream"));
        assertNull(response.getHeaders().getFirst("transfer-encoding"));
        server.verify();
    }

    @Test
    void forwardShouldPropagateHttpErrorResponse() {
        RestTemplate restTemplate = new RestTemplate();
        HttpProxyService service = new HttpProxyService(restTemplate);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        MockHttpServletRequest request = new MockHttpServletRequest();

        server.expect(requestTo("http://target-service/api/fail"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"message\":\"invalid\"}"));

        ResponseEntity<String> response = service.forward(
            HttpMethod.GET,
            "http://target-service",
            "/api/fail",
            request,
            null
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("{\"message\":\"invalid\"}", response.getBody());
        server.verify();
    }

    @Test
    void forwardShouldReturnBadGatewayWhenUpstreamUnavailable() {
        RestTemplate restTemplate = new RestTemplate();
        HttpProxyService service = new HttpProxyService(restTemplate);

        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseEntity<String> response = service.forward(
            HttpMethod.GET,
            "http://127.0.0.1:1",
            "/api/down",
            request,
            null
        );

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals("{\"message\":\"Upstream service unavailable\"}", response.getBody());
    }

    private HttpServletRequest requestWithHeaders(String query, String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setQueryString(query);
        request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        request.addHeader(HttpHeaders.HOST, "gateway.local");
        request.addHeader(HttpHeaders.CONTENT_LENGTH, "123");
        request.addHeader("X-Trace-Id", "trace-1");
        return request;
    }
}
