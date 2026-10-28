package com.project.mss.model.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;

import com.project.mss.model.enums.InvoiceItemCategory;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One line of an invoice - a line, not a REF: the same REF appears on several lines of one invoice, sometimes
 * at different values, and merging them would lose which line a received unit came from.
 *
 * The quantity is the one shipped (qCom). It is NOT the sum of the lots below: the XML declares, for each lot,
 * the size of its sterilisation group, which is regularly larger than what was sent (8 units shipped against
 * lots of 1 and 14). Taking the lot quantities as a division of the shipment would invent material.
 */
@Entity
@Table(name = "supplier_invoice_item")
@Getter
@Setter
@NoArgsConstructor
public class SupplierInvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id")
    private SupplierInvoice invoice;

    /** Position in the invoice (nItem), which is what makes repeated REFs distinguishable. */
    @Column(name = "item_number", nullable = false)
    private Integer itemNumber;

    /** Supplier's product code (cProd); for these suppliers it is the REF. */
    @Column(name = "product_code", length = 60)
    private String productCode;

    @Column(length = 14)
    private String gtin;

    @Column(length = 255)
    private String description;

    /** Catalog material, when the GTIN or the REF matches one. Null means it is not in the catalog yet. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "material_id")
    private Material material;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private InvoiceItemCategory category = InvoiceItemCategory.INSTRUMENT;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "received_quantity", nullable = false)
    private Integer receivedQuantity = 0;

    @Column(name = "unit_value", precision = 14, scale = 4)
    private BigDecimal unitValue;

    @OneToMany(mappedBy = "invoiceItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lotNumber ASC")
    @BatchSize(size = 100)
    private List<SupplierInvoiceItemLot> lots = new ArrayList<>();

    /** Units still owed by the supplier on this line. */
    public int missing() {
        return Math.max(0, quantity - receivedQuantity);
    }

    /** A single declared lot is certain; with several, only the label scanned at the entry decides. */
    public boolean hasCertainLot() {
        return lots.size() == 1;
    }
}
