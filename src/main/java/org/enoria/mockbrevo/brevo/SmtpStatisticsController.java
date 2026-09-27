package org.enoria.mockbrevo.brevo;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.enoria.mockbrevo.auth.CurrentAccount;
import org.enoria.mockbrevo.brevo.dto.EmailEventReportResponse;
import org.enoria.mockbrevo.domain.Account;
import org.enoria.mockbrevo.domain.EmailEvent;
import org.enoria.mockbrevo.domain.EmailEventRepository;
import org.enoria.mockbrevo.events.EmailEventService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Transactional email statistics. Reads the shared email-event store. */
@RestController
@RequestMapping("/v3/smtp/statistics")
public class SmtpStatisticsController {

    static final int MAX_LIMIT = 5000;
    static final int MAX_DAYS = 90;

    /** Brevo returns the report date with milliseconds and a numeric offset. */
    private static final DateTimeFormatter REPORT_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx").withZone(ZoneOffset.UTC);

    /** Stored webhook event name → the report's event name. */
    static final Map<String, String> REPORT_EVENT = Map.ofEntries(
            Map.entry("request", "requests"),
            Map.entry("delivered", "delivered"),
            Map.entry("hard_bounce", "hardBounces"),
            Map.entry("soft_bounce", "softBounces"),
            Map.entry("spam", "spam"),
            Map.entry("opened", "opened"),
            Map.entry("unique_opened", "opened"),
            Map.entry("click", "clicks"),
            Map.entry("invalid_email", "invalid"),
            Map.entry("deferred", "deferred"),
            Map.entry("blocked", "blocked"),
            Map.entry("unsubscribed", "unsubscribed"),
            Map.entry("error", "error"),
            Map.entry("proxy_open", "loadedByProxy"),
            Map.entry("unique_proxy_open", "loadedByProxy"));

    private static final Set<String> FILTER_EVENTS = Set.of(
            "bounces", "hardBounces", "softBounces", "delivered", "spam", "requests", "opened", "clicks",
            "invalid", "deferred", "blocked", "unsubscribed", "error", "loadedByProxy");

    /** Events that went through Brevo's sending IP. */
    private static final Set<String> WITH_IP = Set.of("requests", "delivered", "hardBounces", "softBounces", "deferred");

    private final EmailEventRepository events;
    private final EmailEventService eventService;

    public SmtpStatisticsController(EmailEventRepository events, EmailEventService eventService) {
        this.events = events;
        this.eventService = eventService;
    }

    @GetMapping("/events")
    @Transactional(readOnly = true)
    public ResponseEntity<Object> eventReport(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String event,
            @RequestParam(required = false) String tags,
            @RequestParam(required = false) String messageId,
            @RequestParam(required = false) Long templateId,
            @RequestParam(required = false) String sort) {
        Account account = CurrentAccount.require();
        try {
            int lim = limit == null ? 2500 : limit;
            int off = offset == null ? 0 : offset;
            if (lim < 0 || lim > MAX_LIMIT) throw invalid("limit must be between 0 and " + MAX_LIMIT);
            if (off < 0) throw invalid("offset must be 0 or more");
            boolean asc = parseSort(sort);
            Instant[] range = range(startDate, endDate, days);
            if (event != null && !FILTER_EVENTS.contains(event)) throw invalid("event is not valid: " + event);
            Set<String> tagFilter = parseTags(tags);

            Comparator<EmailEvent> byDate = Comparator.comparing(EmailEvent::getOccurredAt)
                    .thenComparing(EmailEvent::getId);
            List<EmailEventReportResponse.Event> page = events
                    .findByAccountAndOccurredAtBetween(account, range[0], range[1]).stream()
                    .filter(e -> REPORT_EVENT.containsKey(e.getEvent()))
                    .filter(e -> email == null || email.equalsIgnoreCase(e.getEmail()))
                    .filter(e -> event == null || matchesEvent(event, REPORT_EVENT.get(e.getEvent())))
                    .filter(e -> messageId == null || messageId.equals(e.getMessageId()))
                    .filter(e -> templateId == null || templateId.equals(e.getTemplateId()))
                    .filter(e -> tagFilter.isEmpty() || eventService.tags(e).stream().anyMatch(tagFilter::contains))
                    .sorted(asc ? byDate : byDate.reversed())
                    .skip(off)
                    .limit(lim)
                    .map(this::toReport)
                    .toList();
            return ResponseEntity.ok(new EmailEventReportResponse(page));
        } catch (BrevoErrors.InvalidParameter e) {
            return BrevoErrors.badRequest(e.getMessage());
        }
    }

    private EmailEventReportResponse.Event toReport(EmailEvent e) {
        String reportEvent = REPORT_EVENT.get(e.getEvent());
        List<String> tags = eventService.tags(e);
        return new EmailEventReportResponse.Event(
                e.getEmail(),
                REPORT_DATE.format(e.getOccurredAt()),
                e.getMessageId(),
                reportEvent,
                "requests".equals(reportEvent) ? null : e.getReason(),
                // Brevo's own CSV export joins several send tags with "|".
                tags.isEmpty() ? null : String.join("|", tags),
                e.getSubject(),
                e.getSenderEmail(),
                WITH_IP.contains(reportEvent) ? "192.0.2.10" : null,
                "clicks".equals(reportEvent) ? e.getLink() : null,
                e.getTemplateId());
    }

    private static boolean matchesEvent(String filter, String reportEvent) {
        if ("bounces".equals(filter)) return "hardBounces".equals(reportEvent) || "softBounces".equals(reportEvent);
        return filter.equals(reportEvent);
    }

    private static boolean parseSort(String sort) {
        if (sort == null || "desc".equals(sort)) return false;
        if ("asc".equals(sort)) return true;
        throw invalid("sort must be asc or desc");
    }

    /** Accepts a JSON-style array ({@code ["a","b"]}) or a comma-separated list. */
    private static Set<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) return Set.of();
        String inner = tags.trim().replaceAll("^\\[|\\]$", "");
        return Arrays.stream(inner.split(","))
                .map(t -> t.trim().replaceAll("^\"|\"$", ""))
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toSet());
    }

    /** @return [from, to] as instants; both dates are inclusive whole UTC days. */
    private static Instant[] range(String startDate, String endDate, Integer days) {
        if ((startDate == null) != (endDate == null)) throw invalid("startDate and endDate must be used together");
        if (startDate != null) {
            if (days != null) throw invalid("days can't be combined with startDate and endDate");
            LocalDate start = parseDate("startDate", startDate);
            LocalDate end = parseDate("endDate", endDate);
            if (end.isBefore(start)) throw invalid("endDate must be on or after startDate");
            return new Instant[] {
                    start.atStartOfDay(ZoneOffset.UTC).toInstant(),
                    end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1)};
        }
        int d = Objects.requireNonNullElse(days, 30);
        if (d < 1 || d > MAX_DAYS) throw invalid("days must be between 1 and " + MAX_DAYS);
        Instant now = Instant.now();
        return new Instant[] {now.minusSeconds(d * 86_400L), now};
    }

    private static LocalDate parseDate(String name, String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw invalid(name + " must be YYYY-MM-DD");
        }
    }

    private static BrevoErrors.InvalidParameter invalid(String message) {
        return new BrevoErrors.InvalidParameter(message);
    }
}
