package com.project.mss.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A tracking code that named an invoice the system does not have yet.
 *
 * It waits here instead of creating the invoice. An invoice whose items nobody knows would announce material
 * without saying which material, and that is worse than no row: it would sit in the alert forever with
 * nothing to receive against it. When that invoice's XML is read, the code is applied by itself.
 */
@Entity
@Table(name = "pending_tracking")
@Getter
@Setter
@NoArgsConstructor
public class PendingTracking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String number;

    @Column(name = "tracking_code", nullable = false, length = 40)
    private String trackingCode;

    /** Emitter when the other invoices of the same e-mail agreed on one; never taken from the sender. */
    @Column(name = "emitter_cnpj", length = 14)
    private String emitterCnpj;

    @Column(name = "source_email", length = 255)
    private String sourceEmail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    public PendingTracking(String number, String trackingCode, String emitterCnpj, String sourceEmail) {
        this.number = number;
        this.trackingCode = trackingCode;
        this.emitterCnpj = emitterCnpj;
        this.sourceEmail = sourceEmail;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
