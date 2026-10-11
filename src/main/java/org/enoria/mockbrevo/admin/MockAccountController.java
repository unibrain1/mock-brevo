package org.enoria.mockbrevo.admin;

import java.util.LinkedHashMap;
import java.util.Map;
import org.enoria.mockbrevo.domain.Account;
import org.enoria.mockbrevo.domain.AccountRepository;
import org.enoria.mockbrevo.domain.BlockedContactRepository;
import org.enoria.mockbrevo.domain.ContactListRepository;
import org.enoria.mockbrevo.domain.ContactRepository;
import org.enoria.mockbrevo.domain.EmailCampaignRepository;
import org.enoria.mockbrevo.domain.EmailEventRepository;
import org.enoria.mockbrevo.domain.FolderRepository;
import org.enoria.mockbrevo.domain.SenderRepository;
import org.enoria.mockbrevo.domain.SentEmailRepository;
import org.enoria.mockbrevo.domain.SmtpTemplateRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deletes one tenant. {@code POST /mock/reset} deletes all of them. A later request with
 * the same api-key provisions a new, empty account.
 */
@RestController
@RequestMapping("/mock-status/accounts")
public class MockAccountController {

    private final AccountRepository accounts;
    private final EmailEventRepository emailEvents;
    private final BlockedContactRepository blocked;
    private final SentEmailRepository sentEmails;
    private final ContactRepository contacts;
    private final ContactListRepository lists;
    private final EmailCampaignRepository campaigns;
    private final SmtpTemplateRepository templates;
    private final SenderRepository senders;
    private final FolderRepository folders;

    public MockAccountController(
            AccountRepository accounts,
            EmailEventRepository emailEvents,
            BlockedContactRepository blocked,
            SentEmailRepository sentEmails,
            ContactRepository contacts,
            ContactListRepository lists,
            EmailCampaignRepository campaigns,
            SmtpTemplateRepository templates,
            SenderRepository senders,
            FolderRepository folders) {
        this.accounts = accounts;
        this.emailEvents = emailEvents;
        this.blocked = blocked;
        this.sentEmails = sentEmails;
        this.contacts = contacts;
        this.lists = lists;
        this.campaigns = campaigns;
        this.templates = templates;
        this.senders = senders;
        this.folders = folders;
    }

    /** One transaction: a failure deletes nothing. Child rows go first, in the order of {@code MockResetController}. */
    @DeleteMapping("/{apiKey}")
    @Transactional
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String apiKey) {
        Account found = accounts.findByApiKey(apiKey).orElse(null);
        if (found == null) {
            return ResponseEntity.notFound().build();
        }
        // Locked, so block and unblock for this key wait. Any other concurrent write with this key
        // (a send, an import) makes the final account delete fail on a foreign key, and the whole
        // transaction rolls back: nothing is deleted.
        Account account = accounts.lockById(found.getId()).orElseThrow();

        Map<String, Object> deleted = new LinkedHashMap<>();
        deleted.put("emailEvents", emailEvents.deleteByAccount(account));
        deleted.put("blockedContacts", blocked.deleteByAccount(account));
        deleted.put("sentEmails", sentEmails.deleteByAccount(account));
        deleted.put("contacts", contacts.deleteByAccount(account));
        deleted.put("lists", lists.deleteByAccount(account));
        deleted.put("campaigns", campaigns.deleteByAccount(account));
        deleted.put("templates", templates.deleteByAccount(account));
        deleted.put("senders", senders.deleteByAccount(account));
        deleted.put("folders", folders.deleteByAccount(account));
        accounts.delete(account);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "deleted");
        body.put("deleted", deleted);
        return ResponseEntity.ok(body);
    }
}
