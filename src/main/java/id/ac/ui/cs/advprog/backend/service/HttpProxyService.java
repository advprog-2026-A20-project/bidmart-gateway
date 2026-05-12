package id.ac.ui.cs.advprog.backend.service;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class HttpProxyService {

    private static final List<String> REQUEST_HEADER_EXCLUDE = List.of("host", "content-length");
    private static final List<String> RESPONSE_HEADER_EXCLUDE = List.of("transfer-encoding", "content-length");

    private final RestTemplate restTemplate;

    public HttpProxyService(@Qualifier("proxyRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public ResponseEntity<String> forward(
        HttpMethod method,
        String targetBaseUrl,
        String targetPath,
        HttpServletRequest request,
        String body
    ) {
        URI uri = UriComponentsBuilder.fromHttpUrl(targetBaseUrl)
            .path(targetPath)
            .query(request.getQueryString())
            .build(true)
            .toUri();

        HttpHeaders headers = copyRequestHeaders(request);
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(uri, method, entity, String.class);
            return ResponseEntity.status(response.getStatusCode())
                .headers(filterResponseHeaders(response.getHeaders()))
                .body(response.getBody());
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                .headers(filterResponseHeaders(ex.getResponseHeaders()))
                .body(ex.getResponseBodyAsString());
        } catch (ResourceAccessException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body("{\"message\":\"Upstream service unavailable\"}");
        }
    }

    private HttpHeaders copyRequestHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        Collections.list(request.getHeaderNames()).forEach(headerName -> {
            if (REQUEST_HEADER_EXCLUDE.contains(headerName.toLowerCase())) {
                return;
            }
            Collections.list(request.getHeaders(headerName)).forEach(v -> headers.add(headerName, v));
        });
        return headers;
    }

    private HttpHeaders filterResponseHeaders(HttpHeaders originalHeaders) {
        HttpHeaders headers = new HttpHeaders();
        if (originalHeaders == null) {
            return headers;
        }
        originalHeaders.forEach((key, values) -> {
            if (!RESPONSE_HEADER_EXCLUDE.contains(key.toLowerCase())) {
                headers.put(key, values);
            }
        });
        return headers;
    }
}
