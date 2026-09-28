package org.enoria.mockbrevo.admin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.enoria.mockbrevo.auth.AccountService;
import org.enoria.mockbrevo.domain.Account;
import org.enoria.mockbrevo.domain.AccountRepository;
import org.enoria.mockbrevo.domain.SentEmailRepository;
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
    private final AccountRepository accounts;
    private final SentEmailRepository sentEmails;

    public MockWebhooksController(
            WebhookService webhookService,
            AccountService accountService,
            AccountRepository accounts,
            SentEmailRepository sentEmails) {
        this.webhookService = webhookService;
        this.accountService = accountService;
        this.accounts = accounts;
        this.sentEmails = sentEmails;
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
        // Keep inside the email_event column widths, so a long value is a 400 and not a failed write.
        for (MaxLength m : List.of(
                new MaxLength("event", body.event, 40),
                new MaxLength("email", body.email, 320),
                new MaxLength("messageId", body.messageId, 80),
                new MaxLength("reason", body.reason, 1000),
                new MaxLength("link", body.link, 2000))) {
            if (m.value() != null && m.value().length() > m.max()) {
                return ResponseEntity.badRequest().body(
                        Map.of("error", m.field() + " is longer than " + m.max() + " characters"));
            }
        }
        // An event with no known messageId still needs an account to be recorded. Provision
        // only then, so a rejected fire (another account's messageId) leaves no new account.
        Account account = null;
        if (body.apiKey != null && !body.apiKey.isBlank()) {
            account = accounts.findByApiKey(body.apiKey).orElse(null);
            boolean knownMessage = body.messageId != null && sentEmails.findByMessageId(body.messageId).isPresent();
            if (account == null && !knownMessage) {
                account = accountService.resolveOrProvision(body.apiKey);
            } else if (account == null) {
                return ResponseEntity.badRequest().body(Map.of("error", new WebhookService.AccountMismatch().getMessage()));
            }
        }
        boolean recorded;
        try {
            recorded = webhookService.fire(new WebhookService.Fire(
                    body.url, body.token, account, body.event, body.email,
                    body.reason, body.messageId, body.link, body.tags));
        } catch (WebhookService.AccountMismatch e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "fired");
        out.put("event", body.event);
        out.put("email", body.email);
        out.put("recorded", recorded);
        return ResponseEntity.accepted().body(out);
    }

    private record MaxLength(String field, String value, int max) {}

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
