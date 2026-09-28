package org.enoria.mockbrevo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One entry in an account's transactional block list (Brevo's suppression list).
 * The unique constraint is case-sensitive, but lookups ignore case: write only through
 * BlockedContactService, which finds the existing entry first.
 */
@Entity
@Table(name = "blocked_contact",
        indexes = @Index(columnList = "account_id"),
        uniqueConstraints = @UniqueConstraint(columnNames = {"account_id", "email"}))
@Getter
@Setter
@NoArgsConstructor
public class BlockedContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(length = 200)
    private String senderEmail;

    /** Brevo reason code: hardBounce, contactFlaggedAsSpam, unsubscribedViaEmail, ... */
    @Column(nullable = false, length = 40)
    private String reasonCode;

    @Column(nullable = false)
    private Instant blockedAt;
}
