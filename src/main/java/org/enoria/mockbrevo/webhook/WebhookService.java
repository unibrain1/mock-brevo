package org.enoria.mockbrevo.webhook;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.enoria.mockbrevo.brevo.dto.SendSmtpEmailRequest;
import org.enoria.mockbrevo.config.MockBrevoProperties;
import org.enoria.mockbrevo.domain.Account;
import org.enoria.mockbrevo.domain.Contact;
import org.enoria.mockbrevo.domain.ContactRepository;
import org.enoria.mockbrevo.domain.SentEmail;
import org.enoria.mockbrevo.domain.SentEmailRepository;
import org.enoria.mockbrevo.events.EmailEventService;
import org.enoria.mockbrevo.events.EmailEventService.EventData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Builds a Brevo-shaped webhook payload, records the event in the shared store and
 * hands the payload to {@link WebhookSender}. The field set per event follows real
 * Brevo deliveries (see README "Webhook simulation").
 */
@Service
public class WebhookService {

    /** Brevo sends {@code date} as local time without a zone; the mock uses UTC. */
    private static final DateTimeFormatter BREVO_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    /** A documentation address (RFC 5737), so it can't be mistaken for a real sending IP. */
    static final String SENDING_IP = "192.0.2.10";
    static final String USER_AGENT_OF_READER =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko)";

    private static final Set<String> ENGAGEMENT =
            Set.of("unique_opened", "opened", "click", "unsubscribed", "proxy_open", "unique_proxy_open");
    private static final Map<String, String> DEFAULT_REASON = Map.of(
            "delivered", "sent",
            "hard_bounce", "550 5.1.1 The email account that you tried to reach does not exist",
            "soft_bounce", "452 4.2.2 The email account that you tried to reach is over quota",
            "blocked", "blocked : due to blacklist user",
            "deferred", "421 4.7.0 Try again later",
            "error", "error");

    private final SentEmailRepository sentEmails;
    private final ContactRepository contacts;
    private final EmailEventService events;
    private final WebhookSender sender;
    private final MockBrevoProperties properties;
    private final ObjectMapper objectMapper;

    public WebhookService(
            SentEmailRepository sentEmails,
            ContactRepository contacts,
            EmailEventService events,
            WebhookSender sender,
            MockBrevoProperties properties,
            ObjectMapper objectMapper) {
        this.sentEmails = sentEmails;
        this.contacts = contacts;
        this.events = events;
        this.sender = sender;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * What to fire. {@code account} may be null: it is then taken from the sent email
     * that {@code messageId} names. {@code tags} (if not null) replaces the email's tags.
     * {@code token} (if null) falls back to {@code mock-brevo.webhook-token}.
     */
    public record Fire(
            String url,
            String token,
            Account account,
            String event,
            String email,
            String reason,
            String messageId,
            String link,
            List<String> tags) {}

    /** @return true if the event was recorded (an account was known). */
    @Transactional
    public boolean fire(Fire f) {
        Optional<SentEmail> sent = Optional.ofNullable(f.messageId()).flatMap(sentEmails::findByMessageId);
        Account account = sent.map(SentEmail::getAccount).orElse(f.account());
        SendSmtpEmailRequest request = sent.map(this::originalRequest).orElse(null);

        List<String> tags = f.tags() != null ? f.tags()
                : request != null && request.tags() != null ? request.tags() : List.of();
        String reason = f.reason() != null ? f.reason() : DEFAULT_REASON.getOrDefault(f.event(), "sent");
        Instant now = Instant.now();
        // Real Brevo events always carry a message-id; receivers may reject one without it.
        String messageId = f.messageId() != null ? f.messageId() : "<" + UUID.randomUUID() + "@mock-brevo.local>";
        EventData data = new EventData(
                messageId,
                f.email(),
                f.event(),
                reason,
                "click".equals(f.event()) ? Optional.ofNullable(f.link()).orElse("https://example.com/") : f.link(),
                tags,
                sent.map(SentEmail::getSubject).orElse(null),
                sent.map(SentEmail::getSenderEmail).orElse(null),
                sent.map(SentEmail::getTemplateId).orElse(null),
                now);

        if (account != null) {
            events.record(account, data);
        }

        String token = f.token() != null ? f.token() : properties.getWebhookToken();
        sender.send(f.url(), token, payload(account, data, request));
        return account != null;
    }

    private Map<String, Object> payload(Account account, EventData d, SendSmtpEmailRequest request) {
        String event = d.event();
        boolean spam = "spam".equals(event);
        boolean invalid = "invalid_email".equals(event);

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("event", event);
        p.put("email", d.email());
        p.put("id", account != null ? account.getId() : 1L);
        p.put("date", BREVO_DATE.format(d.occurredAt()));
        p.put("ts", d.occurredAt().getEpochSecond());
        p.put("ts_event", d.occurredAt().getEpochSecond());
        p.put("ts_epoch", d.occurredAt().toEpochMilli());
        p.put("message-id", d.messageId());
        p.put("subject", d.subject());
        p.put("tags", d.tags());
        if (!invalid) {
            if (!d.tags().isEmpty()) p.put("tag", d.tags().getFirst());
            p.put("sender_email", d.senderEmail());
            p.put("uuid", UUID.randomUUID().toString());
            if (!spam) p.put("reason", d.reason());
            if (!spam) p.put("sending_ip", SENDING_IP);
            String custom = request != null && request.headers() != null ? request.headers().get("X-Mailin-custom") : null;
            if (custom != null) p.put("X-Mailin-custom", custom);
        }
        if (!spam) p.put("template_id", d.templateId());
        if (ENGAGEMENT.contains(event)) {
            p.put("contact_id", Optional.ofNullable(account)
                    .flatMap(a -> contacts.findByAccountAndEmail(a, d.email()))
                    .map(Contact::getId)
                    .orElse(0L));
            p.put("device_used", "DESKTOP");
            p.put("user_agent", USER_AGENT_OF_READER);
            p.put("mirror_link", "http://localhost:8080/mirror/" + UUID.randomUUID());
            if ("click".equals(event)) p.put("link", d.link());
        }
        return p;
    }

    private SendSmtpEmailRequest originalRequest(SentEmail email) {
        if (email.getPayloadJson() == null) return null;
        try {
            return objectMapper.readValue(email.getPayloadJson(), SendSmtpEmailRequest.class);
        } catch (JacksonException e) {
            return null;
        }
    }
}
