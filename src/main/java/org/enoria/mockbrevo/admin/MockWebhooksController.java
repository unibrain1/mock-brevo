package org.enoria.mockbrevo.admin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.enoria.mockbrevo.auth.AccountService;
import org.enoria.mockbrevo.domain.Account;
import org.enoria.mockbrevo.webhook.WebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mock-webhooks")
public class MockWebhooksController {

    private final WebhookService webhookService;
    private final AccountService accountService;

    public MockWebhooksController(WebhookService webhookService, AccountService accountService) {
        this.webhookService = webhookService;
        this.accountService = accountService;
    }

    @PostMapping("/fire")
    public ResponseEntity<Map<String, Object>> fire(@RequestBody FireRequest body) {
        if (body == null || body.url == null || body.url.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "url is required"));
        }
        if (body.event == null || body.event.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "event is required"));
        }
        if (body.email == null || body.email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "email is required"));
        }
        // An event with no known messageId still needs an account to be recorded.
        Account account = body.apiKey != null && !body.apiKey.isBlank()
                ? accountService.resolveOrProvision(body.apiKey)
                : null;
        boolean recorded = webhookService.fire(new WebhookService.Fire(
                body.url, body.token, account, body.event, body.email,
                body.reason, body.messageId, body.link, body.tags));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "fired");
        out.put("event", body.event);
        out.put("email", body.email);
        out.put("recorded", recorded);
        return ResponseEntity.accepted().body(out);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FireRequest {
        public String url;
        public String event;
        public String email;
        public String reason;
        public String messageId;
        /** Sent as {@code Authorization: Bearer <token>}; overrides {@code MOCK_WEBHOOK_TOKEN}. */
        public String token;
        /** Replaces the tags copied from the sent email. */
        public List<String> tags;
        /** Account to record the event under when {@code messageId} is unknown. */
        public String apiKey;
        /** Clicked URL, for {@code click}. */
        public String link;
    }
}
