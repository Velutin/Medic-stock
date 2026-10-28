package com.project.mss.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.project.mss.model.entity.SupplierInvoiceItem;
import com.project.mss.model.enums.InvoiceStatus;

public interface SupplierInvoiceItemRepository extends JpaRepository<SupplierInvoiceItem, Long> {

    /**
     * Items still owed whose invoice declares this lot, oldest invoice first.
     *
     * The lot is the only automatic criterion: matching by REF would be a guess, because the same REF sits on
     * many lines of many open invoices and a wrong guess writes a receipt against an invoice that is still on
     * its way. Oldest first because that is the one about to become an alert.
     */
    @Query("""
            SELECT it FROM SupplierInvoiceItem it
            JOIN it.lots l
            WHERE l.lotKey = :lotKey
              AND it.invoice.status IN :statuses
              AND it.receivedQuantity < it.quantity
            ORDER BY COALESCE(it.invoice.issuedAt, it.invoice.createdAt) ASC, it.invoice.id ASC, it.itemNumber ASC
            """)
    List<SupplierInvoiceItem> owedByLot(@Param("lotKey") String lotKey,
                                        @Param("statuses") Collection<InvoiceStatus> statuses);

    /** Items of the invoices in a tracking group that are still owed (used for the instrument confirmation). */
    @Query("""
            SELECT it FROM SupplierInvoiceItem it
            WHERE it.invoice.id IN :invoiceIds AND it.receivedQuantity < it.quantity
            ORDER BY it.invoice.id ASC, it.itemNumber ASC
            """)
    List<SupplierInvoiceItem> owedOfInvoices(@Param("invoiceIds") Collection<Long> invoiceIds);

    /** Items whose material is not in the catalog yet, matched by GTIN when it is registered later. */
    List<SupplierInvoiceItem> findByMaterialIsNullAndGtin(String gtin);
}
