package com.project.mss.model.entity;

import java.time.LocalDate;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A lot the XML declares for an item: a candidate, never a share of the shipment.
 *
 * The lot key is the number as the rest of the system compares lots - upper case, no leading zeros - so the
 * entry can match 005706061 against 5706061, the same physical lot written two ways.
 */
@Entity
@Table(name = "supplier_invoice_item_lot")
@Getter
@Setter
@NoArgsConstructor
public class SupplierInvoiceItemLot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_item_id")
    private SupplierInvoiceItem invoiceItem;

    @Column(name = "lot_number", nullable = false, length = 80)
    private String lotNumber;

    @Column(name = "lot_key", nullable = false, length = 80)
    private String lotKey;

    @Column(name = "manufactured_at")
    private LocalDate manufacturedAt;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    /** Size of the sterilisation group declared for this lot; not how many were sent. */
    private Integer quantity;

    public SupplierInvoiceItemLot(SupplierInvoiceItem item, String lotNumber, LocalDate manufacturedAt,
                                  LocalDate expiryDate, Integer quantity) {
        this.invoiceItem = item;
        this.lotNumber = lotNumber == null ? null : lotNumber.trim().toUpperCase();
        this.lotKey = Lot.comparableNumber(lotNumber);
        this.manufacturedAt = manufacturedAt;
        this.expiryDate = expiryDate;
        this.quantity = quantity;
    }

    @PrePersist
    @PreUpdate
    protected void normalise() {
        if (lotNumber != null) lotNumber = lotNumber.trim().toUpperCase();
        lotKey = Lot.comparableNumber(lotNumber);
    }
}
