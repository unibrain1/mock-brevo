package org.enoria.mockbrevo.config;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(MockBrevoProperties.class)
public class AppConfig {

    /**
     * Built from Spring Boot's builder so webhook payloads are serialized with the
     * same JSON mapper configuration as the /v3 responses.
     *
     * Brevo sends webhooks as plain HTTP/1.1 with a Content-Length. The JDK client
     * would otherwise stream the body (chunked) and try an h2c upgrade, which some
     * receivers reject. Buffering sets the Content-Length.
     */
    @Bean
    public RestClient restClient(RestClient.Builder builder) {
        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        return builder
                .requestFactory(new BufferingClientHttpRequestFactory(new JdkClientHttpRequestFactory(http)))
                .defaultHeader(HttpHeaders.USER_AGENT, "Brevo-webhook/2.0 (mock-brevo)")
                .build();
    }
}
