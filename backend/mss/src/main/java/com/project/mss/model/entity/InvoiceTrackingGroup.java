package com.project.mss.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An invoice that travelled under a tracking code, as the supplier's e-mail stated it.
 *
 * This is what lets the entry of the implants ask about the instruments that came in the same box: the link
 * is the code the supplier declared, never a guess from dates being close to each other.
 */
@Entity
@Table(name = "invoice_tracking_group")
@Getter
@Setter
@NoArgsConstructor
public class InvoiceTrackingGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tracking_code", nullable = false, length = 40)
    private String trackingCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id")
    private SupplierInvoice invoice;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public InvoiceTrackingGroup(String trackingCode, SupplierInvoice invoice) {
        this.trackingCode = trackingCode;
        this.invoice = invoice;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
