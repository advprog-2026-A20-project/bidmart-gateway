package id.ac.ui.cs.advprog.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpProxyServiceTest {

    @Test
    void forwardCopiesAuthorizationHeaderToUpstreamRequest() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        HttpProxyService service = new HttpProxyService(restTemplate);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer test-token");

        server.expect(requestTo("http://bidding-command-service:8084/api/auctions"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer test-token"))
            .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        var response = service.forward(
            HttpMethod.POST,
            "http://bidding-command-service:8084",
            "/api/auctions",
            request,
            "{}"
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        server.verify();
    }
}
