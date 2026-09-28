package org.enoria.mockbrevo.events;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.enoria.mockbrevo.domain.Account;
import org.enoria.mockbrevo.domain.AccountRepository;
import org.enoria.mockbrevo.domain.BlockedContact;
import org.enoria.mockbrevo.domain.BlockedContactRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final AccountRepository accounts;

    public BlockedContactService(BlockedContactRepository blocked, AccountRepository accounts) {
        this.blocked = blocked;
        this.accounts = accounts;
    }

    /** Blocks the recipient if {@code event} is one Brevo blocks on. */
    @Transactional
    public void onEvent(Account account, String event, String email, String senderEmail, Instant at) {
        String code = BLOCKING_EVENTS.get(event);
        if (code != null) block(account, email, senderEmail, code, at);
    }

    /**
     * Adds or updates the entry; the latest {@code blockedAt} wins, as in Brevo. Runs in the
     * caller's transaction, so the entry commits (or rolls back) with its email event. The
     * account row is locked first, so two overlapping fires for the same new address can't
     * both insert and break the (account, email) unique constraint.
     */
    @Transactional
    public BlockedContact block(Account account, String email, String senderEmail, String reasonCode, Instant at) {
        lockAccount(account);
        BlockedContact c = blocked.findByAccountAndEmailIgnoreCase(account, email).orElseGet(BlockedContact::new);
        if (c.getBlockedAt() != null && c.getBlockedAt().isAfter(at)) return c;
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
        lockAccount(account);
        Optional<BlockedContact> c = blocked.findByAccountAndEmailIgnoreCase(account, email);
        c.ifPresent(blocked::delete);
        return c.isPresent();
    }

    /** Serializes block and unblock for one account; the lock holds until the transaction ends. */
    private void lockAccount(Account account) {
        accounts.lockById(account.getId())
                .orElseThrow(() -> new IllegalStateException("account " + account.getId() + " no longer exists"));
    }
}
