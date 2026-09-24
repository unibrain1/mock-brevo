package org.enoria.mockbrevo.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(MockBrevoProperties.class)
public class AppConfig {

    /**
     * Built from Spring Boot's builder so webhook payloads are serialized with the
     * same JSON mapper configuration as the /v3 responses.
     */
    @Bean
    public RestClient restClient(RestClient.Builder builder) {
        return builder.build();
    }
}
