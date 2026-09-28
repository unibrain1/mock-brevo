package org.enoria.mockbrevo.events;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.enoria.mockbrevo.domain.Account;
import org.enoria.mockbrevo.domain.BlockedContact;
import org.enoria.mockbrevo.domain.BlockedContactRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** The transactional block list: filled by fired events or seeded through admin routes. */
@Service
public class BlockedContactService {

    /**
     * Brevo reason code → Brevo's message. The first three are from a real export
     * (ElanRegistry's spike); the last three are best guesses, not verified Brevo text.
     */
    public static final Map<String, String> REASONS = Map.of(
            "hardBounce", "This contact's email address generated a hard bounce",
            "contactFlaggedAsSpam", "Contact has flagged one of your emails as junk",
            "unsubscribedViaEmail", "Contact has unsubscribed from your emails.",
            "unsubscribedViaMA", "Contact has unsubscribed via a marketing automation workflow.",
            "unsubscribedViaApi", "Contact has been unsubscribed via the API.",
            "adminBlocked", "Contact has been blocked by an administrator.");

    /** Webhook events that put a contact on the block list. */
    private static final Map<String, String> BLOCKING_EVENTS = Map.of(
            "hard_bounce", "hardBounce",
            "spam", "contactFlaggedAsSpam",
            "unsubscribed", "unsubscribedViaEmail");

    private final BlockedContactRepository blocked;
    private final TransactionTemplate newTransaction;
    /** One JVM: this lock plus the inner commit makes find-then-insert safe for overlapping fires. */
    private final Object upsertLock = new Object();

    public BlockedContactService(BlockedContactRepository blocked, PlatformTransactionManager transactions) {
        this.blocked = blocked;
        this.newTransaction = new TransactionTemplate(transactions);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Blocks the recipient if {@code event} is one Brevo blocks on. */
    public void onEvent(Account account, String event, String email, String senderEmail, Instant at) {
        String code = BLOCKING_EVENTS.get(event);
        if (code != null) block(account, email, senderEmail, code, at);
    }

    /**
     * Adds or updates the entry; the latest block wins, as in Brevo. The upsert commits
     * in its own transaction inside the lock, so two overlapping fires for the same new
     * address can't both insert and break the (account, email) unique constraint.
     */
    public BlockedContact block(Account account, String email, String senderEmail, String reasonCode, Instant at) {
        synchronized (upsertLock) {
            return newTransaction.execute(status -> upsert(account, email, senderEmail, reasonCode, at));
        }
    }

    private BlockedContact upsert(Account account, String email, String senderEmail, String reasonCode, Instant at) {
        BlockedContact c = blocked.findByAccountAndEmailIgnoreCase(account, email).orElseGet(BlockedContact::new);
        c.setAccount(account);
        c.setEmail(email);
        // senderEmail is required by the Brevo SDK model, so fall back to the account's own address.
        c.setSenderEmail(senderEmail != null ? senderEmail : account.getEmail());
        c.setReasonCode(reasonCode);
        c.setBlockedAt(at);
        return blocked.save(c);
    }

    /** @return true if the contact was on the list */
    @Transactional
    public boolean unblock(Account account, String email) {
        Optional<BlockedContact> c = blocked.findByAccountAndEmailIgnoreCase(account, email);
        c.ifPresent(blocked::delete);
        return c.isPresent();
    }
}
