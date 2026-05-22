package id.ac.ui.cs.advprog.backend.config;

import org.springframework.beans.factory.annotation.Qualifier;
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
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMs());
        requestFactory.setReadTimeout(properties.getReadTimeoutMs());
        return new RestTemplate(requestFactory);
    }

    @Bean
    @Qualifier("listingQueryRestTemplate")
    public RestTemplate listingQueryRestTemplate(ListingQueryServiceProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMs());
        requestFactory.setReadTimeout(properties.getReadTimeoutMs());
        return new RestTemplate(requestFactory);
    }
}
