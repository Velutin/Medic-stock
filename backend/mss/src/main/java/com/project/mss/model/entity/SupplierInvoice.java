package com.project.mss.model.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;

import com.project.mss.model.enums.InvoiceStatus;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An invoice the supplier e-mailed, announcing material that left the factory.
 *
 * It is not stock: nothing here counts in a balance, a report or a value. What it gives is the one thing the
 * stock cannot give on its own - the difference between material that never left and material that left and
 * did not arrive. An invoice still open after a few days is what gets charged to the supplier.
 */
@Entity
@Table(name = "supplier_invoice")
@Getter
@Setter
@NoArgsConstructor
public class SupplierInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * NF-e access key, 44 digits: UF, year and month, emitter's CNPJ, model, series, number, emission type,
     * numeric code and a check digit over the other 43. It identifies the invoice without consulting anything,
     * which is what lets the printed barcode be scanned at the entry.
     */
    @Column(name = "access_key", length = 44, unique = true)
    private String accessKey;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "supplier_id")
    private InvoiceSupplier supplier;

    /** Kept as text too: a tracking e-mail may name an invoice before its XML is read. */
    @Column(name = "emitter_cnpj", length = 14)
    private String emitterCnpj;

    @Column(name = "emitter_name", length = 120)
    private String emitterName;

    @Column(nullable = false, length = 20)
    private String number;

    @Column(length = 5)
    private String series;

    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    @Column(length = 120)
    private String operation;

    @Column(length = 4)
    private String cfop;

    /** Recipient printed on the invoice; on OPME shipments it is the hospital, not us. Information only. */
    @Column(name = "recipient_name", length = 140)
    private String recipientName;

    @Column(name = "recipient_cnpj", length = 14)
    private String recipientCnpj;

    @Column(name = "carrier_name", length = 140)
    private String carrierName;

    @Column(name = "tracking_code", length = 40)
    private String trackingCode;

    /** Surgery date when the invoice carries one. Patient and surgeon are deliberately not stored. */
    @Column(name = "surgery_date")
    private LocalDate surgeryDate;

    @Column(name = "total_value", precision = 14, scale = 2)
    private BigDecimal totalValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status = InvoiceStatus.OPEN;

    @Column(name = "received_at")
    private LocalDate receivedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by")
    private User closedBy;

    @Column(name = "close_reason", length = 255)
    private String closeReason;

    /**
     * The DANFE that came with the invoice, kept beside the sheets. The XML carries every fact; this is the
     * document to show the supplier in a disagreement, and it is deleted on its own after a while - what
     * costs space is the file, never the invoice.
     */
    @Column(name = "pdf_file", length = 255)
    private String pdfFile;

    @Column(name = "pdf_stored_at")
    private LocalDateTime pdfStoredAt;

    /** Set when the file was deleted by age, so the screen says "expired" and not "never had one". */
    @Column(name = "pdf_expired_at")
    private LocalDateTime pdfExpiredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("itemNumber ASC")
    @BatchSize(size = 50)
    private List<SupplierInvoiceItem> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** Days since the invoice was issued; what the alert counts. */
    public long daysOpen(LocalDate today) {
        LocalDate issued = issuedAt != null ? issuedAt.toLocalDate() : createdAt.toLocalDate();
        return java.time.temporal.ChronoUnit.DAYS.between(issued, today);
    }

    public int announcedUnits() {
        return items.stream().mapToInt(SupplierInvoiceItem::getQuantity).sum();
    }

    public int receivedUnits() {
        return items.stream().mapToInt(SupplierInvoiceItem::getReceivedQuantity).sum();
    }

    /**
     * Recalculates the status from the items, leaving the states that were decided by a person or by the
     * nature of the invoice untouched: a closed invoice stays closed, and one filed as not a shipment is
     * not a shipment no matter what is written against its items.
     */
    public void refreshStatus() {
        if (status == InvoiceStatus.CLOSED || status == InvoiceStatus.NOT_SHIPMENT) return;
        if (items.isEmpty()) {
            status = InvoiceStatus.AWAITING_XML;
            return;
        }
        int announced = announcedUnits();
        int received = receivedUnits();
        if (received <= 0) {
            status = InvoiceStatus.OPEN;
            receivedAt = null;
        } else if (received < announced) {
            status = InvoiceStatus.PARTIAL;
        } else {
            status = InvoiceStatus.RECEIVED;
            if (receivedAt == null) receivedAt = LocalDate.now();
        }
    }
}
