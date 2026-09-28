package org.enoria.mockbrevo.brevo.dto;

import java.util.List;

/** {@code GET /v3/smtp/statistics/events}. Absent optional fields are left out (global non_null). */
public record EmailEventReportResponse(List<Event> events) {

    public record Event(
            String email,
            String date,
            String messageId,
            String event,
            String reason,
            String tag,
            String subject,
            String from,
            String ip,
            String link,
            Long templateId) {}
}
