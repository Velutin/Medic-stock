package com.project.mss.model.entity;

import java.time.LocalDateTime;

import com.project.mss.model.enums.IncomingEmailStatus;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A message read from the invoice mailbox, whatever came of it.
 *
 * Messages the reader does not understand are rows here, not silence. The one failure this feature cannot
 * afford is the one nobody sees: an e-mail that arrived, was opened and left no trace would put the system
 * back exactly where the audit found it.
 */
@Entity
@Table(name = "incoming_email")
@Getter
@Setter
@NoArgsConstructor
public class IncomingEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Mailbox identifier of the message, so a second reading does not register it twice. */
    @Column(name = "message_uid", nullable = false, unique = true, length = 120)
    private String messageUid;

    @Column(length = 255)
    private String sender;

    @Column(length = 255)
    private String subject;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "read_at", nullable = false)
    private LocalDateTime readAt = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncomingEmailStatus status;

    @Column(nullable = false)
    private Integer attachments = 0;

    /** Invoices created from this message. */
    @Column(nullable = false)
    private Integer invoices = 0;

    @Column(columnDefinition = "TEXT")
    private String detail;
}
