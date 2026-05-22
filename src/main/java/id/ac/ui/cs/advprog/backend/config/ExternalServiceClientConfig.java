package id.ac.ui.cs.advprog.backend.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties({
    AuctionQueryServiceProperties.class,
    ListingQueryServiceProperties.class
})
public class ExternalServiceClientConfig {

    @Bean
    @Qualifier("auctionQueryRestTemplate")
    public RestTemplate auctionQueryRestTemplate(AuctionQueryServiceProperties properties) {
        return restTemplate(properties.getConnectTimeoutMs(), properties.getReadTimeoutMs());
    }

    @Bean
    @Qualifier("listingQueryRestTemplate")
    public RestTemplate listingQueryRestTemplate(ListingQueryServiceProperties properties) {
        return restTemplate(properties.getConnectTimeoutMs(), properties.getReadTimeoutMs());
    }

    @Bean
    @Qualifier("serviceCommandRestTemplate")
    public RestTemplate serviceCommandRestTemplate(
        @Value("${microservices.client.connect-timeout-ms:${SERVICE_CLIENT_CONNECT_TIMEOUT_MS:500}}") int connectTimeoutMs,
        @Value("${microservices.client.read-timeout-ms:${SERVICE_CLIENT_READ_TIMEOUT_MS:1500}}") int readTimeoutMs
    ) {
        return restTemplate(connectTimeoutMs, readTimeoutMs);
    }

    private RestTemplate restTemplate(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(requestFactory);
    }
}
