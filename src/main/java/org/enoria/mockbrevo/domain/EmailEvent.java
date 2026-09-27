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
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One transactional email event (a send request, or a simulated webhook event).
 * The webhook, the event report and the block list all read this table, so one
 * simulated event looks the same everywhere. {@code event} holds the webhook name
 * (hard_bounce, delivered, ...); the event report maps it to its own names.
 */
@Entity
@Table(name = "email_event", indexes = {
        @Index(columnList = "account_id"),
        @Index(columnList = "email"),
        @Index(columnList = "messageId")
})
@Getter
@Setter
@NoArgsConstructor
public class EmailEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(length = 80)
    private String messageId;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(nullable = false, length = 40)
    private String event;

    @Column(length = 1000)
    private String reason;

    @Column(length = 2000)
    private String link;

    /** JSON array of the send tags, e.g. {@code ["registry"]}. */
    @Column(length = 2000)
    private String tagsJson;

    @Column(length = 500)
    private String subject;

    @Column(length = 200)
    private String senderEmail;

    private Long templateId;

    @Column(nullable = false)
    private Instant occurredAt;
}
