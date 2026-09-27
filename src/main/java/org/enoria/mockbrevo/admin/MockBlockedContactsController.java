package org.enoria.mockbrevo.admin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.enoria.mockbrevo.brevo.BlockedContactsController;
import org.enoria.mockbrevo.brevo.dto.BlockedContactsResponse;
import org.enoria.mockbrevo.domain.AccountRepository;
import org.enoria.mockbrevo.domain.BlockedContact;
import org.enoria.mockbrevo.domain.BlockedContactRepository;
import org.enoria.mockbrevo.events.BlockedContactService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin routes to inspect and seed an account's block list without firing events. */
@RestController
@RequestMapping("/mock-status/accounts/{apiKey}/blocked")
public class MockBlockedContactsController {

    private final AccountRepository accounts;
    private final BlockedContactRepository blocked;
    private final BlockedContactService service;

    public MockBlockedContactsController(
            AccountRepository accounts, BlockedContactRepository blocked, BlockedContactService service) {
        this.accounts = accounts;
        this.blocked = blocked;
        this.service = service;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> list(@PathVariable String apiKey) {
        return accounts.findByApiKey(apiKey)
                .map(account -> {
                    List<BlockedContactsResponse.Contact> items = blocked.findByAccount(account).stream()
                            .sorted(Comparator.comparing(BlockedContact::getBlockedAt).reversed())
                            .map(BlockedContactsController::toResponse)
                            .toList();
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("count", items.size());
                    out.put("contacts", items);
                    return ResponseEntity.ok(out);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Object> add(@PathVariable String apiKey, @RequestBody AddRequest body) {
        if (body == null || body.email() == null || body.email().isBlank() || body.email().length() > 320) {
            return ResponseEntity.badRequest().body(Map.of("error", "email is required (max 320 characters)"));
        }
        String code = body.reason() == null || body.reason().isBlank() ? "adminBlocked" : body.reason();
        if (!BlockedContactService.REASONS.containsKey(code)) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "reason must be one of " + String.join(", ", BlockedContactService.REASONS.keySet().stream().sorted().toList())));
        }
        if (body.senderEmail() != null && body.senderEmail().length() > 200) {
            return ResponseEntity.badRequest().body(Map.of("error", "senderEmail is longer than 200 characters"));
        }
        return accounts.findByApiKey(apiKey)
                .<ResponseEntity<Object>>map(account -> ResponseEntity.status(HttpStatus.CREATED).body(
                        BlockedContactsController.toResponse(
                                service.block(account, body.email(), body.senderEmail(), code, Instant.now()))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{email}")
    public ResponseEntity<Void> remove(@PathVariable String apiKey, @PathVariable String email) {
        return accounts.findByApiKey(apiKey)
                .map(account -> service.unblock(account, email)
                        ? ResponseEntity.noContent().<Void>build()
                        : ResponseEntity.notFound().<Void>build())
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** {@code reason} is a Brevo reason code; default {@code adminBlocked}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AddRequest(String email, String senderEmail, String reason) {}
}
