package org.enoria.mockbrevo.events;

import java.time.Instant;
import java.util.List;
import org.enoria.mockbrevo.domain.Account;
import org.enoria.mockbrevo.domain.EmailEvent;
import org.enoria.mockbrevo.domain.EmailEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Writes and decodes the shared email-event store. */
@Service
public class EmailEventService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final EmailEventRepository events;
    private final ObjectMapper objectMapper;

    public EmailEventService(EmailEventRepository events, ObjectMapper objectMapper) {
        this.events = events;
        this.objectMapper = objectMapper;
    }

    /** The details of one event, taken from the sent email when it is known. */
    public record EventData(
            String messageId,
            String email,
            String event,
            String reason,
            String link,
            List<String> tags,
            String subject,
            String senderEmail,
            Long templateId,
            Instant occurredAt) {}

    @Transactional
    public EmailEvent record(Account account, EventData data) {
        EmailEvent e = new EmailEvent();
        e.setAccount(account);
        e.setMessageId(data.messageId());
        e.setEmail(data.email());
        e.setEvent(data.event());
        e.setReason(data.reason());
        e.setLink(data.link());
        e.setTagsJson(writeTags(data.tags()));
        e.setSubject(data.subject());
        e.setSenderEmail(data.senderEmail());
        e.setTemplateId(data.templateId());
        e.setOccurredAt(data.occurredAt());
        return events.save(e);
    }

    public List<String> tags(EmailEvent e) {
        return readTags(e.getTagsJson());
    }

    public String writeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (JacksonException ex) {
            return null;
        }
    }

    public List<String> readTags(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (JacksonException ex) {
            return List.of();
        }
    }
}
