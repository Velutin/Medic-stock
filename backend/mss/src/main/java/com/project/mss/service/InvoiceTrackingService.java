package com.project.mss.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.mss.exception.EntityNotFoundException;
import com.project.mss.model.entity.InvoiceTrackingGroup;
import com.project.mss.model.entity.PendingTracking;
import com.project.mss.model.entity.SupplierInvoice;
import com.project.mss.repository.InvoiceTrackingGroupRepository;
import com.project.mss.repository.PendingTrackingRepository;
import com.project.mss.repository.SupplierInvoiceRepository;

/**
 * Applies a tracking code to the invoices a supplier's e-mail named.
 *
 * The match is by invoice number alone. The sender's address is not used: a tracking e-mail arrived from a
 * baumer.com.br address listing invoices issued by Implus, so the domain says nothing about the emitter. And
 * when the same number exists at two suppliers, nothing is decided automatically - a code written on the
 * wrong invoice would send the wrong box to the wrong conversation.
 *
 * A number with no invoice yet waits in pending_tracking, and lands on its invoice when that XML is read.
 */
@Service
public class InvoiceTrackingService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceTrackingService.class);

    /** What happened to one tracking e-mail, for the mailbox log. */
    public record TrackingResult(String code, int applied, int waiting, List<String> ambiguous) {

        public String describe() {
            StringBuilder text = new StringBuilder(code).append(": ")
                    .append(applied).append(applied == 1 ? " nota" : " notas");
            if (waiting > 0) text.append(", ").append(waiting).append(" aguardando o XML");
            if (!ambiguous.isEmpty()) text.append(", em dúvida: ").append(String.join(", ", ambiguous));
            return text.toString();
        }
    }

    private final SupplierInvoiceRepository invoiceRepository;
    private final PendingTrackingRepository pendingRepository;
    private final InvoiceTrackingGroupRepository groupRepository;
    private final AccessControlService accessControlService;

    public InvoiceTrackingService(SupplierInvoiceRepository invoiceRepository,
                                  PendingTrackingRepository pendingRepository,
                                  InvoiceTrackingGroupRepository groupRepository,
                                  AccessControlService accessControlService) {
        this.invoiceRepository = invoiceRepository;
        this.pendingRepository = pendingRepository;
        this.groupRepository = groupRepository;
        this.accessControlService = accessControlService;
    }

    /**
     * Writes the code on every invoice named, and files the ones that do not exist yet.
     *
     * The emitter is worked out from the invoices that did match: when they all come from the same supplier,
     * the ones still missing are almost certainly from it too, and that guess only narrows a later search -
     * it never creates anything.
     */
    @Transactional
    public TrackingResult apply(String code, List<String> numbers, String sender) {
        List<SupplierInvoice> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> ambiguous = new ArrayList<>();

        for (String number : numbers) {
            List<SupplierInvoice> found = invoiceRepository.findByNumber(number);
            if (found.isEmpty()) {
                missing.add(number);
            } else if (found.size() > 1) {
                // The same number at two suppliers: left for the administrator instead of guessed
                ambiguous.add(number);
            } else {
                matched.add(found.get(0));
            }
        }

        for (SupplierInvoice invoice : matched) {
            invoice.setTrackingCode(code);
            invoiceRepository.save(invoice);
            if (!groupRepository.existsByTrackingCodeAndInvoiceId(code, invoice.getId())) {
                groupRepository.save(new InvoiceTrackingGroup(code, invoice));
            }
        }

        String emitter = commonEmitter(matched);
        for (String number : missing) {
            if (pendingRepository.existsByNumberAndTrackingCode(number, code)) continue;
            pendingRepository.save(new PendingTracking(number, code, emitter, sender));
        }

        TrackingResult result = new TrackingResult(code, matched.size(), missing.size(), ambiguous);
        log.info("Tracking code {}: {}", code, result.describe());
        return result;
    }

    /**
     * Tracking codes that were waiting for this invoice. Called right after an XML is read, so the order the
     * two e-mails arrive in stops mattering.
     */
    @Transactional
    public void applyWaiting(SupplierInvoice invoice) {
        if (invoice.getNumber() == null) return;
        List<PendingTracking> waiting = pendingRepository.findByNumberAndAppliedAtIsNull(invoice.getNumber());
        for (PendingTracking pending : waiting) {
            // An emitter recorded with the code has to agree, otherwise this is another supplier's invoice
            // that happens to carry the same number
            if (pending.getEmitterCnpj() != null && invoice.getEmitterCnpj() != null
                    && !pending.getEmitterCnpj().equals(invoice.getEmitterCnpj())) {
                continue;
            }
            invoice.setTrackingCode(pending.getTrackingCode());
            pending.setAppliedAt(LocalDateTime.now());
            pendingRepository.save(pending);
            if (!groupRepository.existsByTrackingCodeAndInvoiceId(pending.getTrackingCode(), invoice.getId())) {
                groupRepository.save(new InvoiceTrackingGroup(pending.getTrackingCode(), invoice));
            }
            log.info("Tracking code {} applied to invoice {} when its XML arrived",
                    pending.getTrackingCode(), invoice.getNumber());
            return;
        }
    }

    /** Codes still waiting for their invoice, newest first. */
    @Transactional(readOnly = true)
    public List<com.project.mss.dto.invoice.PendingTrackingDTO> waiting() {
        return pendingRepository.findByAppliedAtIsNullOrderByCreatedAtDesc().stream()
                .map(com.project.mss.dto.invoice.PendingTrackingDTO::of)
                .toList();
    }

    /**
     * Drops a code that will never find its invoice.
     *
     * It happens for real: invoices issued before the mailbox existed were named by a tracking e-mail that
     * arrived later, and their XML is never coming. Without this they would wait forever, and a list of
     * things waiting forever is a list nobody reads.
     */
    @Transactional
    public void discard(Long id) {
        accessControlService.requireManager();
        PendingTracking pending = pendingRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Pending tracking " + id + " not found"));
        pendingRepository.delete(pending);
        log.info("Tracking code {} for invoice {} discarded", pending.getTrackingCode(), pending.getNumber());
    }

    /** Emitter shared by every matched invoice; null when they disagree or there are none. */
    private static String commonEmitter(List<SupplierInvoice> invoices) {
        Optional<String> first = invoices.stream().map(SupplierInvoice::getEmitterCnpj)
                .filter(java.util.Objects::nonNull).findFirst();
        if (first.isEmpty()) return null;
        boolean same = invoices.stream().map(SupplierInvoice::getEmitterCnpj)
                .filter(java.util.Objects::nonNull)
                .allMatch(cnpj -> cnpj.equals(first.get()));
        return same ? first.get() : null;
    }
}
