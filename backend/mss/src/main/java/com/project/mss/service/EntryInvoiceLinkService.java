package com.project.mss.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.mss.dto.entry.EntryInvoiceSummaryDTO;
import com.project.mss.dto.entry.InvoiceLotDTO;
import com.project.mss.exception.BusinessRuleException;
import com.project.mss.exception.EntityNotFoundException;
import com.project.mss.model.entity.Lot;
import com.project.mss.model.entity.Material;
import com.project.mss.model.entity.PendingIssue;
import com.project.mss.model.entity.StockEntry;
import com.project.mss.model.entity.StockEntryItem;
import com.project.mss.model.entity.StockEntryItemInvoice;
import com.project.mss.model.entity.SupplierInvoice;
import com.project.mss.model.entity.SupplierInvoiceItem;
import com.project.mss.model.entity.SupplierInvoiceItemLot;
import com.project.mss.model.enums.InvoiceItemCategory;
import com.project.mss.model.enums.InvoiceLinkOrigin;
import com.project.mss.model.enums.InvoiceStatus;
import com.project.mss.model.enums.PendingIssueReason;
import com.project.mss.model.enums.PendingIssueStatus;
import com.project.mss.repository.PendingIssueRepository;
import com.project.mss.repository.StockEntryItemInvoiceRepository;
import com.project.mss.repository.SupplierInvoiceItemLotRepository;
import com.project.mss.repository.SupplierInvoiceItemRepository;
import com.project.mss.repository.SupplierInvoiceRepository;
import com.project.mss.util.NfeAccessKey;

/**
 * Writes what an entry received against the invoices that announced it.
 *
 * The match is by lot number and nothing else. Matching by REF was considered and dropped: the same REF sits
 * on many lines of many open invoices, so a REF match would regularly write a receipt against an invoice
 * still in transit - and it would do it silently, which is the worst kind of wrong.
 *
 * Quantities beyond what an invoice announced do not force it: the surplus looks for another open invoice
 * carrying the same lot, and only then is left unlinked. One invoice never goes over its own numbers.
 */
@Service
public class EntryInvoiceLinkService {

    private static final List<InvoiceStatus> OPEN =
            List.of(InvoiceStatus.OPEN, InvoiceStatus.PARTIAL, InvoiceStatus.AWAITING_XML);

    private final SupplierInvoiceItemRepository invoiceItemRepository;
    private final SupplierInvoiceItemLotRepository lotRepository;
    private final SupplierInvoiceRepository invoiceRepository;
    private final StockEntryItemInvoiceRepository linkRepository;
    private final AppSettingService settingService;
    private final PendingIssueRepository pendingIssueRepository;
    private final AccessControlService accessControlService;

    public EntryInvoiceLinkService(SupplierInvoiceItemRepository invoiceItemRepository,
                                   SupplierInvoiceItemLotRepository lotRepository,
                                   SupplierInvoiceRepository invoiceRepository,
                                   StockEntryItemInvoiceRepository linkRepository,
                                   AppSettingService settingService, PendingIssueRepository pendingIssueRepository,
                                   AccessControlService accessControlService) {
        this.invoiceItemRepository = invoiceItemRepository;
        this.lotRepository = lotRepository;
        this.invoiceRepository = invoiceRepository;
        this.linkRepository = linkRepository;
        this.settingService = settingService;
        this.pendingIssueRepository = pendingIssueRepository;
        this.accessControlService = accessControlService;
    }

    /**
     * Links the items of an entry to the invoices that owe them.
     *
     * accessKeys are the DANFEs scanned at the entry. They do not restrict the match - a box regularly holds
     * items of an invoice whose barcode nobody scanned - they only come first, because the invoice in your
     * hand is the likeliest owner of what you are unpacking.
     */
    @Transactional
    public EntryInvoiceSummaryDTO link(StockEntry entry, List<String> accessKeys) {
        Set<Long> preferred = preferredInvoices(accessKeys);
        List<StockEntryItemInvoice> written = new ArrayList<>();
        List<EntryInvoiceSummaryDTO.UnlinkedLine> unlinked = new ArrayList<>();
        Map<StockEntryItem, Integer> withoutInvoice = new LinkedHashMap<>();

        for (StockEntryItem item : entry.getItems()) {
            int remaining = item.getQuantity();
            String lotKey = Lot.comparableNumber(item.getLot().getNumber());

            List<SupplierInvoiceItem> owed = new ArrayList<>(invoiceItemRepository.owedByLot(lotKey, OPEN));
            // Invoices whose DANFE was scanned come first; the rest keeps the oldest-first order, which is
            // the one about to become an alert
            owed.sort(Comparator.comparing(i -> preferred.contains(i.getInvoice().getId()) ? 0 : 1));

            for (SupplierInvoiceItem invoiceItem : owed) {
                if (remaining <= 0) break;
                int take = Math.min(remaining, invoiceItem.missing());
                if (take <= 0) continue;
                invoiceItem.setReceivedQuantity(invoiceItem.getReceivedQuantity() + take);
                written.add(linkRepository.save(new StockEntryItemInvoice(item, invoiceItem, take,
                        InvoiceLinkOrigin.AUTO_LOT, accessControlService.currentUser())));
                remaining -= take;
            }

            if (remaining > 0) {
                unlinked.add(new EntryInvoiceSummaryDTO.UnlinkedLine(item.getLot().getMaterial().getRef(),
                        item.getLot().getNumber(), remaining));
                withoutInvoice.put(item, remaining);
            }
        }

        Set<SupplierInvoice> touched = new LinkedHashSet<>();
        written.forEach(l -> touched.add(l.getInvoiceItem().getInvoice()));
        touched.forEach(invoice -> {
            invoice.refreshStatus();
            invoiceRepository.save(invoice);
        });

        // Invoices scanned at the entry are reported even when nothing of theirs was read, which is the
        // point: an invoice in your hand with nothing received from it is the one to look into
        accessKeys(accessKeys).forEach(touched::add);

        int raised = raisePendingIssues(entry, withoutInvoice);
        return summary(touched, unlinked, instrumentsAwaiting(touched), raised);
    }

    /**
     * Undoes the links of an entry, giving the received quantities back to the invoices.
     *
     * Open "without invoice" issues of this entry go too: they were raised by a state the correction is
     * replacing, and the relink that follows raises them again for whatever is still missing. Issues
     * already handled stay, because they are what someone decided and that is history.
     */
    @Transactional
    public void unlink(Long entryId) {
        pendingIssueRepository.deleteAll(pendingIssueRepository.ofEntry(entryId, PendingIssueStatus.OPEN));
        List<StockEntryItemInvoice> links = linkRepository.ofEntry(entryId);
        Set<SupplierInvoice> touched = new LinkedHashSet<>();
        for (StockEntryItemInvoice link : links) {
            SupplierInvoiceItem item = link.getInvoiceItem();
            item.setReceivedQuantity(Math.max(0, item.getReceivedQuantity() - link.getQuantity()));
            touched.add(item.getInvoice());
            linkRepository.delete(link);
        }
        touched.forEach(invoice -> {
            invoice.refreshStatus();
            invoiceRepository.save(invoice);
        });
    }

    /**
     * Records instruments as received. They arrive loose, with no label to scan, so what is recorded is the
     * administrator saying they were in the box - and it is recorded as a confirmation, not as a scan.
     */
    @Transactional
    public EntryInvoiceSummaryDTO confirmInstruments(List<Long> invoiceItemIds) {
        accessControlService.requireManager();
        Set<SupplierInvoice> touched = new LinkedHashSet<>();
        for (Long id : invoiceItemIds) {
            SupplierInvoiceItem item = invoiceItemRepository.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Invoice item " + id + " not found"));
            if (item.getCategory() != InvoiceItemCategory.INSTRUMENT) {
                throw new BusinessRuleException("Only an instrument is received by confirmation; "
                        + "an implant is received by scanning its label");
            }
            int missing = item.missing();
            if (missing <= 0) continue;
            item.setReceivedQuantity(item.getQuantity());
            linkRepository.save(new StockEntryItemInvoice(null, item, missing,
                    InvoiceLinkOrigin.CONFIRMED, accessControlService.currentUser()));
            touched.add(item.getInvoice());
        }
        touched.forEach(invoice -> {
            invoice.refreshStatus();
            invoiceRepository.save(invoice);
        });
        return summary(touched, List.of(), List.of(), 0);
    }

    // ============================================================ helpers

    /** Invoices of the scanned DANFEs. An unknown key is not an error: its invoice may not have been read yet. */
    private Set<Long> preferredInvoices(List<String> accessKeys) {
        Set<Long> ids = new LinkedHashSet<>();
        accessKeys(accessKeys).forEach(invoice -> ids.add(invoice.getId()));
        return ids;
    }

    private List<SupplierInvoice> accessKeys(List<String> accessKeys) {
        if (accessKeys == null || accessKeys.isEmpty()) return List.of();
        List<SupplierInvoice> invoices = new ArrayList<>();
        for (String key : accessKeys) {
            NfeAccessKey parsed = NfeAccessKey.parse(key);
            if (parsed == null) continue;
            invoiceRepository.findByAccessKey(parsed.digits()).ifPresent(invoices::add);
        }
        return invoices;
    }

    /**
     * Instruments still owed on the invoices that travelled under the same tracking code as the invoices
     * this entry settled. This is what lets the entry ask "the drill came in this box too, did it arrive?"
     * - and it only asks when the supplier itself said they travelled together.
     */
    private List<EntryInvoiceSummaryDTO.InstrumentLine> instrumentsAwaiting(Set<SupplierInvoice> touched) {
        Set<String> codes = new LinkedHashSet<>();
        touched.stream().map(SupplierInvoice::getTrackingCode).filter(c -> c != null && !c.isBlank())
                .forEach(codes::add);
        if (codes.isEmpty()) return List.of();

        Set<Long> invoiceIds = new LinkedHashSet<>();
        for (String code : codes) {
            invoiceRepository.openByTracking(code, OPEN).forEach(i -> invoiceIds.add(i.getId()));
        }
        if (invoiceIds.isEmpty()) return List.of();

        return invoiceItemRepository.owedOfInvoices(invoiceIds).stream()
                .filter(i -> i.getCategory() == InvoiceItemCategory.INSTRUMENT)
                .map(i -> new EntryInvoiceSummaryDTO.InstrumentLine(i.getId(), i.getInvoice().getNumber(),
                        i.getProductCode(), i.getDescription(), i.missing(), i.getInvoice().getTrackingCode()))
                .toList();
    }

    /**
     * Raises a pending issue for each lot that entered with no invoice owing it, when the invoice is required.
     *
     * It does not refuse the entry. Refusing was the first design and it was wrong: the material is in the
     * box, it was counted and its labels were read, and throwing that away over an e-mail that has not
     * arrived punishes the person at the bench for the supplier's delay. The stock is registered; what is
     * missing is the paperwork, and the paperwork is settled by the administrator in Pendências - by naming
     * the invoice, or by recording that this one really came without one.
     *
     * With the requirement off, nothing is raised: while the invoices are still catching up every lot would
     * become a pending issue, and a list of everything is a list of nothing.
     */
    private int raisePendingIssues(StockEntry entry, Map<StockEntryItem, Integer> withoutInvoice) {
        if (!settingService.invoiceRequiredOnEntry() || withoutInvoice.isEmpty()) return 0;
        for (Map.Entry<StockEntryItem, Integer> line : withoutInvoice.entrySet()) {
            StockEntryItem item = line.getKey();
            PendingIssue issue = new PendingIssue();
            issue.setHospital(entry.getHospital());
            issue.setStockEntryItem(item);
            issue.setEnteredCode(item.getLot().getNumber());
            issue.setEnteredRef(item.getLot().getMaterial().getRef());
            issue.setQuantity(line.getValue());
            issue.setReason(PendingIssueReason.WITHOUT_INVOICE);
            issue.setStatus(PendingIssueStatus.OPEN);
            pendingIssueRepository.save(issue);
        }
        return withoutInvoice.size();
    }

    /**
     * Writes a lot that entered without an invoice against the invoice the administrator named.
     *
     * The invoice has to be in the system already. Accepting a number nobody has read would create an
     * invoice that announces material without saying which - the same reason a tracking e-mail never
     * invents one. When the XML has not arrived, the answer is to ask the supplier to send it again, and
     * the pending issue waits.
     */
    @Transactional
    public void linkToInvoice(PendingIssue issue, String invoiceNumber) {
        StockEntryItem item = issue.getStockEntryItem();
        if (item == null) {
            throw new BusinessRuleException("This pending issue is not about a missing invoice");
        }
        String typed = invoiceNumber == null ? "" : invoiceNumber.trim();
        List<SupplierInvoice> candidates = findByNumber(typed);
        if (candidates.isEmpty()) {
            throw new BusinessRuleException("Invoice " + typed
                    + " is not registered yet: ask the supplier to send the XML again");
        }
        if (candidates.stream().allMatch(i -> i.getStatus() == InvoiceStatus.CLOSED)) {
            throw new BusinessRuleException("Invoice " + typed + " is closed and cannot receive this lot");
        }

        // Two suppliers can print the same number: the lot is what says which of them this is
        String lotKey = Lot.comparableNumber(item.getLot().getNumber());
        SupplierInvoice invoice = null;
        SupplierInvoiceItem owing = null;
        for (SupplierInvoice candidate : candidates) {
            if (candidate.getStatus() == InvoiceStatus.CLOSED) continue;
            Optional<SupplierInvoiceItem> match = candidate.getItems().stream()
                    .filter(i -> i.missing() > 0)
                    .filter(i -> i.getLots().stream().anyMatch(l -> lotKey.equals(l.getLotKey())))
                    .findFirst();
            if (match.isPresent()) {
                invoice = candidate;
                owing = match.get();
                break;
            }
        }
        if (owing == null) {
            throw new BusinessRuleException("Invoice " + typed + " does not owe lot "
                    + item.getLot().getNumber());
        }

        int take = Math.min(issue.getQuantity(), owing.missing());
        owing.setReceivedQuantity(owing.getReceivedQuantity() + take);
        linkRepository.save(new StockEntryItemInvoice(item, owing, take, InvoiceLinkOrigin.MANUAL,
                accessControlService.currentUser()));
        invoice.refreshStatus();
        invoiceRepository.save(invoice);
    }

    /** The invoice as it is printed, or without the leading zeros the person may have typed. */
    private List<SupplierInvoice> findByNumber(String typed) {
        if (typed.isBlank()) return List.of();
        List<SupplierInvoice> found = invoiceRepository.findByNumber(typed);
        if (found.isEmpty()) {
            String withoutZeros = typed.replaceFirst("^0+", "");
            if (!withoutZeros.equals(typed) && !withoutZeros.isBlank()) {
                found = invoiceRepository.findByNumber(withoutZeros);
            }
        }
        return found.stream().filter(i -> i.getStatus() != InvoiceStatus.NOT_SHIPMENT).toList();
    }

    private EntryInvoiceSummaryDTO summary(Set<SupplierInvoice> invoices,
                                           List<EntryInvoiceSummaryDTO.UnlinkedLine> unlinked,
                                           List<EntryInvoiceSummaryDTO.InstrumentLine> instruments,
                                           int pendingIssues) {
        Map<Long, EntryInvoiceSummaryDTO.InvoiceLine> lines = new LinkedHashMap<>();
        for (SupplierInvoice invoice : invoices) {
            List<EntryInvoiceSummaryDTO.MissingLine> missing = invoice.getItems().stream()
                    .filter(i -> i.missing() > 0)
                    .map(i -> new EntryInvoiceSummaryDTO.MissingLine(i.getId(), i.getProductCode(),
                            i.getDescription(), i.missing(), i.getCategory().name()))
                    .toList();
            lines.put(invoice.getId(), new EntryInvoiceSummaryDTO.InvoiceLine(invoice.getId(), invoice.getNumber(),
                    invoice.getEmitterName(), invoice.getStatus().name(), invoice.getTrackingCode(),
                    invoice.receivedUnits(), invoice.announcedUnits(), missing));
        }
        return new EntryInvoiceSummaryDTO(List.copyOf(lines.values()), unlinked, instruments, pendingIssues);
    }

    /**
     * The lot an open invoice is owing, so the entry can be made by reading the lot alone.
     *
     * Today an item that comes with a barcode takes three steps: read the REF, read the lot, type the expiry
     * date. The invoice already carries all three, declared by the manufacturer, so reading the lot is
     * enough - and the two things that were typed stop being typed, which is two fewer chances of a typo.
     *
     * Nothing is answered when the lot is owed by invoices that disagree about the REF or about the expiry
     * date: filling the screen with one of two possibilities would be guessing, and the ordinary flow (read
     * the REF, type the date) is right there.
     */
    @Transactional(readOnly = true)
    public Optional<InvoiceLotDTO> lookupLot(String lotNumber) {
        accessControlService.requireManager();
        if (lotNumber == null || lotNumber.isBlank()) return Optional.empty();

        List<SupplierInvoiceItemLot> found = lotRepository.owedByKey(Lot.comparableNumber(lotNumber), OPEN);
        List<SupplierInvoiceItemLot> usable = found.stream()
                .filter(l -> l.getInvoiceItem().getMaterial() != null && l.getExpiryDate() != null)
                .toList();
        if (usable.isEmpty()) return Optional.empty();

        Set<Long> materials = new LinkedHashSet<>();
        Set<java.time.LocalDate> expiries = new LinkedHashSet<>();
        usable.forEach(l -> {
            materials.add(l.getInvoiceItem().getMaterial().getId());
            expiries.add(l.getExpiryDate());
        });
        if (materials.size() > 1 || expiries.size() > 1) return Optional.empty();

        SupplierInvoiceItemLot lot = usable.get(0);
        SupplierInvoiceItem item = lot.getInvoiceItem();
        Material material = item.getMaterial();
        return Optional.of(new InvoiceLotDTO(material.getId(), material.getRef(), material.getDescription(),
                material.getComponent(), material.getSize(), material.getColor(), lot.getLotNumber(),
                lot.getExpiryDate(), lot.getExpiryDate().getDayOfMonth() == 1,
                item.getInvoice().getId(), item.getInvoice().getNumber(), item.getInvoice().getEmitterName(),
                item.missing()));
    }

    /** Links already written for an entry, for the screen that shows it again. */
    @Transactional(readOnly = true)
    public EntryInvoiceSummaryDTO ofEntry(Long entryId) {
        Set<SupplierInvoice> invoices = new LinkedHashSet<>();
        for (Long invoiceId : linkRepository.invoiceIdsOfEntry(entryId)) {
            Optional<SupplierInvoice> invoice = invoiceRepository.findById(invoiceId);
            invoice.ifPresent(invoices::add);
        }
        return summary(invoices, List.of(), List.of(), 0);
    }
}
