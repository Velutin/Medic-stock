package com.project.mss.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.project.mss.dto.invoice.InvoiceDTO;
import com.project.mss.dto.invoice.InvoiceImportResultDTO;
import com.project.mss.exception.BusinessRuleException;
import com.project.mss.exception.EntityNotFoundException;
import com.project.mss.model.entity.InvoiceSupplier;
import com.project.mss.model.entity.Lot;
import com.project.mss.model.entity.Material;
import com.project.mss.model.entity.SupplierInvoice;
import com.project.mss.model.entity.SupplierInvoiceItem;
import com.project.mss.model.entity.SupplierInvoiceItemLot;
import com.project.mss.model.enums.InvoiceItemCategory;
import com.project.mss.model.enums.InvoiceStatus;
import com.project.mss.repository.InvoiceSupplierRepository;
import com.project.mss.repository.MaterialRepository;
import com.project.mss.repository.SupplierInvoiceRepository;
import com.project.mss.service.NfeXmlParser.ParsedInvoice;
import com.project.mss.service.NfeXmlParser.ParsedItem;
import com.project.mss.service.NfeXmlParser.ParsedLot;

/**
 * Invoices the supplier announced: importing their XML, listing them and closing them.
 *
 * What is imported is only what serves the control of what arrived. The invoice is never stock and never a
 * value in a report; it is the record that material left the factory, so that material which never arrives
 * can be seen and charged instead of discovered months later in an audit.
 */
@Service
public class SupplierInvoiceService {

    /** CFOPs of a shipment coming to us. A return (2918) is deliberately absent. */
    private static final Set<String> SHIPMENT_CFOPS = Set.of("5917", "6917", "5949", "6949");

    private final SupplierInvoiceRepository invoiceRepository;
    private final InvoiceSupplierRepository supplierRepository;
    private final MaterialRepository materialRepository;
    private final NfeXmlParser parser;
    private final AppSettingService settingService;
    private final AccessControlService accessControlService;
    private final InvoiceTrackingService trackingService;
    private final FileStorageService fileStorageService;

    public SupplierInvoiceService(SupplierInvoiceRepository invoiceRepository,
                                  InvoiceSupplierRepository supplierRepository,
                                  MaterialRepository materialRepository, NfeXmlParser parser,
                                  AppSettingService settingService, AccessControlService accessControlService,
                                  InvoiceTrackingService trackingService, FileStorageService fileStorageService) {
        this.invoiceRepository = invoiceRepository;
        this.supplierRepository = supplierRepository;
        this.materialRepository = materialRepository;
        this.parser = parser;
        this.settingService = settingService;
        this.accessControlService = accessControlService;
        this.trackingService = trackingService;
        this.fileStorageService = fileStorageService;
    }

    // ============================================================ import

    /**
     * Imports one NF-e XML. Called by the mailbox reader and by the manual upload, so it answers with what it
     * did instead of throwing when the invoice is simply not for us - "already registered" and "not a
     * shipment" are ordinary outcomes of reading a mailbox, not errors.
     */
    /**
     * Each XML in its own transaction (REQUIRES_NEW), and not the caller's.
     *
     * One e-mail carries several attachments, and the reader catches the failure of one so the others go
     * through. Sharing the transaction defeats that: a refusal by the database marks the whole transaction
     * as rollback-only, the catch cannot undo that mark, and at commit everything is thrown away - the log
     * row of the e-mail included. The message would then stay unread and come back every hour, forever,
     * with nothing anywhere saying why.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public InvoiceImportResultDTO importXml(byte[] xml) {
        ParsedInvoice parsed = parser.parse(xml);

        Optional<SupplierInvoice> existing = invoiceRepository.findByAccessKey(parsed.accessKey());
        if (existing.isPresent() && !existing.get().getItems().isEmpty()) {
            return InvoiceImportResultDTO.duplicate(InvoiceDTO.of(existing.get(), settingService.alertDays()));
        }

        InvoiceSupplier supplier = supplierRepository.findByCnpj(parsed.emitterCnpj()).orElse(null);
        SupplierInvoice invoice = existing.orElseGet(SupplierInvoice::new);
        fill(invoice, parsed, supplier);

        // Not a shipment: a return, an unknown emitter or a CFOP outside the list. It is filed, not dropped:
        // an invoice nobody can see is exactly what this feature exists to stop.
        String refusal = shipmentRefusal(parsed, supplier);
        if (refusal != null) {
            invoice.setStatus(InvoiceStatus.NOT_SHIPMENT);
            invoice.getItems().clear();
            SupplierInvoice saved = invoiceRepository.save(invoice);
            return InvoiceImportResultDTO.notShipment(InvoiceDTO.of(saved, settingService.alertDays()), refusal);
        }

        invoice.getItems().clear();
        for (ParsedItem item : parsed.items()) {
            invoice.getItems().add(item(invoice, item));
        }
        invoice.refreshStatus();
        SupplierInvoice saved = invoiceRepository.save(invoice);
        // A tracking e-mail may have named this invoice before its XML arrived; the code lands on it now
        trackingService.applyWaiting(saved);
        return InvoiceImportResultDTO.imported(InvoiceDTO.of(saved, settingService.alertDays()));
    }

    /** Manual upload of an XML, for the first weeks and for whatever the reader could not handle. */
    @Transactional
    public InvoiceImportResultDTO upload(byte[] xml) {
        accessControlService.requireManager();
        return importXml(xml);
    }

    private void fill(SupplierInvoice invoice, ParsedInvoice parsed, InvoiceSupplier supplier) {
        invoice.setAccessKey(parsed.accessKey());
        invoice.setSupplier(supplier);
        invoice.setEmitterCnpj(parsed.emitterCnpj());
        invoice.setEmitterName(parsed.emitterName());
        invoice.setNumber(parsed.number());
        invoice.setSeries(parsed.series());
        invoice.setIssuedAt(parsed.issuedAt());
        invoice.setOperation(parsed.operation());
        invoice.setCfop(parsed.cfop());
        invoice.setRecipientName(parsed.recipientName());
        invoice.setRecipientCnpj(parsed.recipientCnpj());
        invoice.setCarrierName(parsed.carrierName());
        invoice.setSurgeryDate(parsed.surgeryDate());
        invoice.setTotalValue(parsed.totalValue());
        // A tracking code already known (from the tracking e-mail) is kept; the XML rarely carries one
        if (invoice.getTrackingCode() == null) invoice.setTrackingCode(parsed.trackingCode());
    }

    /** Why this invoice is not material coming to us; null when it is. */
    private String shipmentRefusal(ParsedInvoice parsed, InvoiceSupplier supplier) {
        if (supplier == null || !Boolean.TRUE.equals(supplier.getActive())) {
            return "Emitter " + parsed.emitterName() + " is not a registered supplier";
        }
        if (!parsed.issuedByEmitter()) {
            return "Invoice issued as an entry (tpNF = 0): it is a return, not a shipment";
        }
        String cfop = parsed.cfop();
        if (cfop == null || !SHIPMENT_CFOPS.contains(cfop)) {
            return "CFOP " + cfop + " is not in the shipment list";
        }
        return null;
    }

    /**
     * One line of the invoice. The category comes from the catalog: a GTIN registered in Materials is an
     * implant, received by scanning its label; anything else is an instrument, received by confirmation.
     * Registering the material at the entry moves it to implant from then on.
     */
    private SupplierInvoiceItem item(SupplierInvoice invoice, ParsedItem parsed) {
        SupplierInvoiceItem item = new SupplierInvoiceItem();
        item.setInvoice(invoice);
        item.setItemNumber(parsed.itemNumber());
        item.setProductCode(parsed.productCode());
        item.setGtin(parsed.gtin());
        item.setDescription(parsed.description());
        item.setQuantity(Math.max(1, parsed.quantity()));
        item.setUnitValue(parsed.unitValue());

        Material material = material(parsed);
        item.setMaterial(material);
        item.setCategory(material != null ? InvoiceItemCategory.IMPLANT : InvoiceItemCategory.INSTRUMENT);

        for (ParsedLot lot : parsed.lots()) {
            if (lot.number() == null) continue;
            item.getLots().add(new SupplierInvoiceItemLot(item, lot.number(), lot.manufacturedAt(),
                    lot.expiryDate(), lot.quantity()));
        }
        return item;
    }

    /**
     * Catalog material of the line. The GTIN comes first because it is what the label carries and what the
     * entry reads; the product code is tried next, since both suppliers use the REF there.
     */
    private Material material(ParsedItem parsed) {
        if (parsed.gtin() != null) {
            Optional<Material> byGtin = materialRepository.findByGtin(parsed.gtin());
            if (byGtin.isPresent()) return byGtin.get();
        }
        if (parsed.productCode() != null) {
            return materialRepository.findByRefIgnoreCase(parsed.productCode().trim()).orElse(null);
        }
        return null;
    }

    /**
     * Stores the DANFE that came with an invoice. Replacing an existing one deletes the old file instead of
     * leaving it orphaned in the folder.
     */
    @Transactional
    public void attachPdf(Long invoiceId, byte[] pdf) {
        SupplierInvoice invoice = load(invoiceId);
        String previous = invoice.getPdfFile();
        invoice.setPdfFile(fileStorageService.saveInvoicePdf(invoice.getId(), pdf));
        invoice.setPdfStoredAt(LocalDateTime.now());
        invoice.setPdfExpiredAt(null);
        invoiceRepository.save(invoice);
        if (previous != null) fileStorageService.delete(previous);
    }

    @Transactional(readOnly = true)
    public byte[] pdf(Long id) {
        accessControlService.requireManager();
        SupplierInvoice invoice = load(id);
        if (invoice.getPdfFile() == null) {
            throw new BusinessRuleException(invoice.getPdfExpiredAt() != null
                    ? "The PDF of this invoice was deleted after the retention period"
                    : "This invoice has no stored PDF");
        }
        return fileStorageService.read(invoice.getPdfFile());
    }

    // ============================================================ queries

    /**
     * Invoices for the screen.
     *
     * search also looks into the lots the invoices declare, which answers "in how many invoices did this lot
     * come". category keeps only the invoices that carry that kind of item: an invoice with no instrument is
     * noise on a screen asking for instruments, and filtering it here rather than on the screen keeps the
     * page count honest.
     */
    @Transactional(readOnly = true)
    public Page<InvoiceDTO> list(String status, String search, String category, Pageable pageable) {
        accessControlService.requireManager();
        List<InvoiceStatus> statuses = statuses(status);
        // '%' instead of null: no filter is "matches everything", never a parameter compared with IS NULL
        String term = search == null || search.isBlank() ? "%" : "%" + search.trim().toLowerCase() + "%";
        String lotKey = lotSearchKey(search);
        int alertDays = settingService.alertDays();
        InvoiceItemCategory wanted = category(category);
        Page<SupplierInvoice> page = wanted == null
                ? invoiceRepository.list(statuses, term, lotKey, pageable)
                : invoiceRepository.listByCategory(statuses, term, lotKey, wanted, pageable);
        return page.map(i -> InvoiceDTO.of(i, alertDays));
    }

    @Transactional(readOnly = true)
    public InvoiceDTO find(Long id) {
        accessControlService.requireManager();
        return InvoiceDTO.of(load(id), settingService.alertDays());
    }

    /**
     * Invoices open for longer than the configured number of days: the ones to charge to the supplier.
     * Counted as invoices and not as items, because one invoice is one conversation with the supplier.
     */
    @Transactional(readOnly = true)
    public List<InvoiceDTO> overdue() {
        int alertDays = settingService.alertDays();
        LocalDate limit = LocalDate.now().minusDays(alertDays);
        return invoiceRepository.overdue(openStatuses(), limit).stream()
                .map(i -> InvoiceDTO.of(i, alertDays))
                .toList();
    }

    /** How many invoices are overdue; the dashboard card. */
    @Transactional(readOnly = true)
    public long countOverdue() {
        return invoiceRepository.overdue(openStatuses(), LocalDate.now().minusDays(settingService.alertDays())).size();
    }

    // ============================================================ changes by hand

    /** Tracking code typed by the administrator, until the supplier starts sending it by e-mail. */
    @Transactional
    public InvoiceDTO setTrackingCode(Long id, String trackingCode) {
        accessControlService.requireManager();
        SupplierInvoice invoice = load(id);
        invoice.setTrackingCode(trackingCode == null || trackingCode.isBlank() ? null : trackingCode.trim().toUpperCase());
        return InvoiceDTO.of(invoiceRepository.save(invoice), settingService.alertDays());
    }

    /**
     * Closes an invoice that will not be received as it is (settled with the supplier, cancelled, lost).
     * The reason is required: an invoice that leaves the alert without a reason is the same blind spot again.
     */
    @Transactional
    public InvoiceDTO close(Long id, String reason) {
        accessControlService.requireManager();
        if (reason == null || reason.isBlank()) throw new BusinessRuleException("Closing reason is required");
        SupplierInvoice invoice = load(id);
        if (invoice.getStatus() == InvoiceStatus.RECEIVED) {
            throw new BusinessRuleException("This invoice was fully received and does not need to be closed");
        }
        invoice.setStatus(InvoiceStatus.CLOSED);
        invoice.setCloseReason(reason.trim());
        invoice.setClosedAt(LocalDateTime.now());
        invoice.setClosedBy(accessControlService.currentUser());
        return InvoiceDTO.of(invoiceRepository.save(invoice), settingService.alertDays());
    }

    /** Reopens an invoice closed by mistake. */
    @Transactional
    public InvoiceDTO reopen(Long id) {
        accessControlService.requireManager();
        SupplierInvoice invoice = load(id);
        if (invoice.getStatus() != InvoiceStatus.CLOSED) {
            throw new BusinessRuleException("Only a closed invoice can be reopened");
        }
        invoice.setStatus(InvoiceStatus.OPEN);
        invoice.setCloseReason(null);
        invoice.setClosedAt(null);
        invoice.setClosedBy(null);
        invoice.refreshStatus();
        return InvoiceDTO.of(invoiceRepository.save(invoice), settingService.alertDays());
    }

    // ============================================================ helpers

    private SupplierInvoice load(Long id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Invoice " + id + " not found"));
    }

    private static List<InvoiceStatus> openStatuses() {
        return List.of(InvoiceStatus.AWAITING_XML, InvoiceStatus.OPEN, InvoiceStatus.PARTIAL);
    }

    /**
     * Lot typed in the search box, as a prefix for the stored lot key.
     *
     * The key is kept without leading zeros, so 005706061 and 5706061 are the same lot. The letter some lots
     * carry at the end (5655208B is the same lot as 5655208) is dropped from the search and the comparison is
     * a prefix, so typing either form finds both.
     */
    private static String lotSearchKey(String search) {
        if (search == null || search.isBlank()) return "%";
        String typed = search.trim().toUpperCase().replaceAll("[A-Z]+$", "");
        if (typed.isBlank()) return "%";
        return Lot.comparableNumber(typed) + "%";
    }

    /** The kind of item asked for, or null when the screen wants every invoice. */
    private static InvoiceItemCategory category(String category) {
        if (category == null || category.isBlank()) return null;
        try {
            return InvoiceItemCategory.valueOf(category.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Unknown item category: " + category);
        }
    }

    /** "OPEN" means every status that still waits for material, which is what the screen filters by. */
    private static List<InvoiceStatus> statuses(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return List.of(InvoiceStatus.values());
        }
        if ("OPEN".equalsIgnoreCase(status)) return openStatuses();
        try {
            return List.of(InvoiceStatus.valueOf(status.toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Unknown invoice status: " + status);
        }
    }
}
