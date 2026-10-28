package com.project.mss.model.entity;

import java.time.LocalDateTime;

import com.project.mss.model.enums.InvoiceLinkOrigin;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What an entry received against what an invoice announced.
 *
 * The link lives here and not on the lot because the same physical lot arrives in more than one invoice: a
 * column on the lot would keep only the last one, and the question an audit asks is exactly the other one -
 * which invoice brought these five units.
 */
@Entity
@Table(name = "stock_entry_item_invoice")
@Getter
@Setter
@NoArgsConstructor
public class StockEntryItemInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null for a confirmed instrument: there is no lot and therefore no entry item. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_entry_item_id")
    private StockEntryItem stockEntryItem;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "invoice_item_id")
    private SupplierInvoiceItem invoiceItem;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private InvoiceLinkOrigin origin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public StockEntryItemInvoice(StockEntryItem entryItem, SupplierInvoiceItem invoiceItem, int quantity,
                                 InvoiceLinkOrigin origin, User createdBy) {
        this.stockEntryItem = entryItem;
        this.invoiceItem = invoiceItem;
        this.quantity = quantity;
        this.origin = origin;
        this.createdBy = createdBy;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
