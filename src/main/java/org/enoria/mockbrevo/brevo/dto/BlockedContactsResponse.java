package org.enoria.mockbrevo.brevo.dto;

import java.util.List;

/** {@code GET /v3/smtp/blockedContacts}. {@code count} is the total matching, not the page size. */
public record BlockedContactsResponse(long count, List<Contact> contacts) {

    public record Contact(String email, String senderEmail, Reason reason, String blockedAt) {}

    public record Reason(String code, String message) {}
}
