package com.project.mss.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.project.mss.model.entity.SupplierInvoice;
import com.project.mss.model.enums.InvoiceItemCategory;
import com.project.mss.model.enums.InvoiceStatus;

public interface SupplierInvoiceRepository extends JpaRepository<SupplierInvoice, Long> {

    Optional<SupplierInvoice> findByAccessKey(String accessKey);

    Optional<SupplierInvoice> findByEmitterCnpjAndSeriesAndNumber(String emitterCnpj, String series, String number);

    /** Invoices with this number, from any supplier: more than one means the number alone does not decide. */
    List<SupplierInvoice> findByNumber(String number);

    /** Invoices whose stored PDF is older than the limit; what the retention job deletes. */
    List<SupplierInvoice> findByPdfFileIsNotNullAndPdfStoredAtBefore(LocalDateTime limit);

    /**
     * Invoices for the screen: by status, by what is typed in the search box and by the kind of item they
     * carry.
     *
     * The item conditions are EXISTS and not joins on purpose. A join against the items multiplies the
     * invoice by its lines, and the DISTINCT that would fix that cannot live with this ORDER BY - PostgreSQL
     * refuses to sort a distinct result by an expression that is not in the select list. EXISTS filters
     * without duplicating anything.
     *
     * No parameter here is ever null, and that is deliberate. statuses and categories always carry a list -
     * every value when the screen asks for all of them - and search and lotKey carry '%', which matches
     * everything. A parameter compared with IS NULL leaves Hibernate with no type for it: it binds the
     * value as binary, and the query dies in PostgreSQL at the first function that touches it. That
     * happened once in this project already, in the movements report.
     */
    @Query("""
            SELECT i FROM SupplierInvoice i
            WHERE i.status IN :statuses
              AND (LOWER(i.number) LIKE :search OR LOWER(i.trackingCode) LIKE :search
                   OR LOWER(i.emitterName) LIKE :search
                   OR EXISTS (SELECT 1 FROM SupplierInvoiceItemLot l
                              WHERE l.invoiceItem.invoice = i AND l.lotKey LIKE :lotKey))
            ORDER BY COALESCE(i.issuedAt, i.createdAt) DESC, i.id DESC
            """)
    Page<SupplierInvoice> list(@Param("statuses") Collection<InvoiceStatus> statuses,
                               @Param("search") String search, @Param("lotKey") String lotKey,
                               Pageable pageable);

    /**
     * The same list keeping only the invoices that carry an item of the chosen kind.
     *
     * A method of its own, and not a nullable parameter on the one above, for two reasons. The EXISTS also
     * drops an invoice with no items at all - right when filtering by kind, since nobody knows yet what an
     * invoice without its XML carries, and wrong when not filtering, where that invoice must be listed. And
     * a parameter that is sometimes null is the binding that already broke one query in this project.
     */
    @Query("""
            SELECT i FROM SupplierInvoice i
            WHERE i.status IN :statuses
              AND (LOWER(i.number) LIKE :search OR LOWER(i.trackingCode) LIKE :search
                   OR LOWER(i.emitterName) LIKE :search
                   OR EXISTS (SELECT 1 FROM SupplierInvoiceItemLot l
                              WHERE l.invoiceItem.invoice = i AND l.lotKey LIKE :lotKey))
              AND EXISTS (SELECT 1 FROM SupplierInvoiceItem it
                          WHERE it.invoice = i AND it.category = :category)
            ORDER BY COALESCE(i.issuedAt, i.createdAt) DESC, i.id DESC
            """)
    Page<SupplierInvoice> listByCategory(@Param("statuses") Collection<InvoiceStatus> statuses,
                                         @Param("search") String search, @Param("lotKey") String lotKey,
                                         @Param("category") InvoiceItemCategory category, Pageable pageable);

    /**
     * Invoices still waiting for material and issued before the cut-off date: what is charged to the supplier.
     * Counted as invoices, not items - one invoice is one conversation with the supplier.
     */
    @Query("""
            SELECT i FROM SupplierInvoice i
            WHERE i.status IN :statuses
              AND COALESCE(CAST(i.issuedAt AS date), CAST(i.createdAt AS date)) <= :limit
            ORDER BY COALESCE(i.issuedAt, i.createdAt) ASC, i.id ASC
            """)
    List<SupplierInvoice> overdue(@Param("statuses") Collection<InvoiceStatus> statuses,
                                  @Param("limit") LocalDate limit);

    /** Open invoices of one tracking code, used to ask about the instruments shipped in the same box. */
    @Query("""
            SELECT i FROM SupplierInvoice i
            WHERE i.trackingCode = :tracking AND i.status IN :statuses
            ORDER BY i.id ASC
            """)
    List<SupplierInvoice> openByTracking(@Param("tracking") String tracking,
                                         @Param("statuses") Collection<InvoiceStatus> statuses);
}
