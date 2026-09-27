package org.enoria.mockbrevo.webhook;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Sends one webhook payload, off the request thread. Best-effort: failures are logged only. */
@Component
public class WebhookSender {

    private static final Logger log = LoggerFactory.getLogger(WebhookSender.class);

    private final RestClient restClient;

    public WebhookSender(RestClient restClient) {
        this.restClient = restClient;
    }

    @Async
    public void send(String url, String token, Map<String, Object> payload) {
        try {
            restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> {
                        if (token != null && !token.isBlank()) h.setBearerAuth(token);
                    })
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            // Never log the token.
            log.info("Webhook fired: event={} email={} url={}", payload.get("event"), payload.get("email"), url);
        } catch (RestClientException e) {
            log.warn("Webhook POST to {} failed: {}", url, e.getMessage());
        }
    }
}
