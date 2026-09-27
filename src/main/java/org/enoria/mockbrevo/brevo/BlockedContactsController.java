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
import java.util.Set;
import java.util.stream.Collectors;
import org.enoria.mockbrevo.auth.CurrentAccount;
import org.enoria.mockbrevo.brevo.dto.BlockedContactsResponse;
import org.enoria.mockbrevo.domain.Account;
import org.enoria.mockbrevo.domain.BlockedContact;
import org.enoria.mockbrevo.domain.BlockedContactRepository;
import org.enoria.mockbrevo.events.BlockedContactService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The transactional block list (suppression list). */
@RestController
@RequestMapping("/v3/smtp/blockedContacts")
public class BlockedContactsController {

    static final int MAX_LIMIT = 100;

    public static final DateTimeFormatter BLOCKED_AT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx").withZone(ZoneOffset.UTC);

    private final BlockedContactRepository blocked;
    private final BlockedContactService service;

    public BlockedContactsController(BlockedContactRepository blocked, BlockedContactService service) {
        this.blocked = blocked;
        this.service = service;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<Object> list(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset,
            @RequestParam(required = false) String senders,
            @RequestParam(required = false) String sort) {
        Account account = CurrentAccount.require();
        try {
            int lim = limit == null ? 50 : limit;
            int off = offset == null ? 0 : offset;
            if (lim < 0 || lim > MAX_LIMIT) throw invalid("limit must be between 0 and " + MAX_LIMIT);
            if (off < 0) throw invalid("offset must be 0 or more");
            boolean asc = parseSort(sort);
            Instant[] range = range(startDate, endDate);
            Set<String> senderFilter = senders == null ? Set.of() : Arrays.stream(senders.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).map(String::toLowerCase)
                    .collect(Collectors.toSet());

            Comparator<BlockedContact> byDate = Comparator.comparing(BlockedContact::getBlockedAt)
                    .thenComparing(BlockedContact::getId);
            List<BlockedContact> matching = blocked.findByAccount(account).stream()
                    .filter(c -> range == null
                            || (!c.getBlockedAt().isBefore(range[0]) && !c.getBlockedAt().isAfter(range[1])))
                    .filter(c -> senderFilter.isEmpty()
                            || (c.getSenderEmail() != null && senderFilter.contains(c.getSenderEmail().toLowerCase())))
                    .sorted(asc ? byDate : byDate.reversed())
                    .toList();
            List<BlockedContactsResponse.Contact> page = matching.stream()
                    .skip(off)
                    .limit(lim)
                    .map(BlockedContactsController::toResponse)
                    .toList();
            return ResponseEntity.ok(new BlockedContactsResponse(matching.size(), page));
        } catch (BrevoErrors.InvalidParameter e) {
            return BrevoErrors.badRequest(e.getMessage());
        }
    }

    /** Unblock or resubscribe a transactional contact. */
    @DeleteMapping("/{email}")
    public ResponseEntity<Object> unblock(@PathVariable String email) {
        Account account = CurrentAccount.require();
        if (service.unblock(account, email)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("code", "document_not_found", "message", "Contact does not exist"));
    }

    public static BlockedContactsResponse.Contact toResponse(BlockedContact c) {
        return new BlockedContactsResponse.Contact(
                c.getEmail(),
                c.getSenderEmail(),
                new BlockedContactsResponse.Reason(c.getReasonCode(),
                        BlockedContactService.REASONS.getOrDefault(c.getReasonCode(), c.getReasonCode())),
                BLOCKED_AT.format(c.getBlockedAt()));
    }

    private static boolean parseSort(String sort) {
        if (sort == null || "desc".equals(sort)) return false;
        if ("asc".equals(sort)) return true;
        throw invalid("sort must be asc or desc");
    }

    /** @return null for no date filter, else [from, to] covering both whole UTC days */
    private static Instant[] range(String startDate, String endDate) {
        if ((startDate == null) != (endDate == null)) throw invalid("startDate and endDate must be used together");
        if (startDate == null) return null;
        LocalDate start = parseDate("startDate", startDate);
        LocalDate end = parseDate("endDate", endDate);
        if (end.isBefore(start)) throw invalid("endDate must be on or after startDate");
        return new Instant[] {
                start.atStartOfDay(ZoneOffset.UTC).toInstant(),
                end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1)};
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
