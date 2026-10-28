package com.project.mss.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.mss.dto.invoice.InboundMailDTO;
import com.project.mss.dto.invoice.IncomingEmailDTO;
import com.project.mss.dto.invoice.InvoiceDTO;
import com.project.mss.dto.invoice.InvoiceImportResultDTO;
import com.project.mss.model.entity.IncomingEmail;
import com.project.mss.model.enums.IncomingEmailStatus;
import com.project.mss.repository.IncomingEmailRepository;
import com.project.mss.service.InvoiceTrackingService.TrackingResult;
import com.project.mss.service.TrackingEmailParser.ParsedTracking;

/**
 * Turns a message read from the mailbox into invoices, and records what happened to it.
 *
 * Three kinds of message arrive: the invoice itself (an XML, usually with its DANFE beside it), the e-mail
 * that says which invoices travelled under a tracking code, and everything else. All three leave a row.
 * A message read and dropped in silence is the failure mode this feature exists to remove: the invitation
 * that vanished into a queue nobody consumed taught that lesson once, and an invoice that never appears is
 * worse, because the material it announced is real.
 */
@Service
public class IncomingEmailService {

    private static final Logger log = LoggerFactory.getLogger(IncomingEmailService.class);

    /** Digits in an attachment name, used to confirm which invoice a PDF belongs to. */
    private static final Pattern NAME_NUMBER = Pattern.compile("\\d{3,}");

    private final IncomingEmailRepository repository;
    private final SupplierInvoiceService invoiceService;
    private final TrackingEmailParser trackingParser;
    private final InvoiceTrackingService trackingService;
    private final AccessControlService accessControlService;

    public IncomingEmailService(IncomingEmailRepository repository, SupplierInvoiceService invoiceService,
                                TrackingEmailParser trackingParser, InvoiceTrackingService trackingService,
                                AccessControlService accessControlService) {
        this.repository = repository;
        this.invoiceService = invoiceService;
        this.trackingParser = trackingParser;
        this.trackingService = trackingService;
        this.accessControlService = accessControlService;
    }

    /**
     * Processes one message. Already-seen messages are skipped by their mailbox id, so a reading that runs
     * twice (a restart, a manual run) does not register anything twice.
     */
    @Transactional
    public void process(InboundMailDTO mail) {
        if (mail == null || mail.messageUid() == null) {
            log.warn("Message without an identifier discarded");
            return;
        }
        // A message already seen is skipped, except when it produced nothing: those are read again, so an
        // improvement to the reading recovers by itself what an older version could not understand. There is
        // nothing to duplicate in them - they created no invoice and applied no tracking code.
        IncomingEmail previous = repository.findByMessageUid(mail.messageUid()).orElse(null);
        if (previous != null && !reprocessable(previous)) return;

        IncomingEmail record = previous != null ? previous : new IncomingEmail();
        record.setMessageUid(mail.messageUid());
        record.setSender(trim(mail.sender(), 255));
        record.setSubject(trim(mail.subject(), 255));
        record.setSentAt(dateTime(mail.sentAt()));
        record.setReadAt(LocalDateTime.now());
        record.setAttachments(mail.attachments() == null ? 0 : mail.attachments().size());

        List<InboundMailDTO.Attachment> xmls = attachments(mail, ".xml");
        if (xmls.isEmpty()) {
            tracking(mail, record);
            repository.save(record);
            return;
        }
        invoices(mail, xmls, record);
        repository.save(record);
    }

    /** Outcomes worth another try: they left no invoice and no tracking code behind. */
    private static boolean reprocessable(IncomingEmail previous) {
        return previous.getStatus() == IncomingEmailStatus.UNRECOGNISED
                || previous.getStatus() == IncomingEmailStatus.FAILED;
    }

    // ============================================================ invoices

    private void invoices(InboundMailDTO mail, List<InboundMailDTO.Attachment> xmls, IncomingEmail record) {
        int imported = 0;
        int duplicates = 0;
        List<String> notes = new ArrayList<>();
        List<InvoiceDTO> registered = new ArrayList<>();

        for (InboundMailDTO.Attachment attachment : xmls) {
            try {
                InvoiceImportResultDTO result = invoiceService.importXml(Base64.getDecoder().decode(attachment.content()));
                switch (result.outcome()) {
                    case IMPORTED -> {
                        imported++;
                        registered.add(result.invoice());
                    }
                    case DUPLICATE -> {
                        duplicates++;
                        registered.add(result.invoice());
                    }
                    case NOT_SHIPMENT -> notes.add(attachment.name() + ": " + result.detail());
                }
            } catch (RuntimeException e) {
                // One bad attachment does not lose the others, and the reason is kept where it can be read
                log.warn("Could not import {} of message {}: {}", attachment.name(), mail.messageUid(), e.getMessage());
                notes.add(attachment.name() + ": " + e.getMessage());
            }
        }

        notes.addAll(storePdfs(mail, registered));

        record.setInvoices(imported);
        if (imported > 0) {
            record.setStatus(IncomingEmailStatus.IMPORTED);
        } else if (!notes.isEmpty()) {
            record.setStatus(IncomingEmailStatus.NOT_SHIPMENT);
        } else if (duplicates > 0) {
            record.setStatus(IncomingEmailStatus.DUPLICATE);
        } else {
            record.setStatus(IncomingEmailStatus.FAILED);
        }
        record.setDetail(notes.isEmpty() ? null : String.join("; ", notes));
    }

    /**
     * Files the DANFEs that came with the invoices of this message.
     *
     * The e-mails seen so far carry one invoice and its PDF, which already settles it. The file name is used
     * as a check, not as the decision: when it carries the number of a different invoice, the PDF is left
     * unattached rather than written onto the wrong invoice - a PDF on the wrong invoice is worse than none.
     * A name with no number in it is no objection: naming files is nobody's obligation.
     */
    private List<String> storePdfs(InboundMailDTO mail, List<InvoiceDTO> invoices) {
        List<InboundMailDTO.Attachment> pdfs = attachments(mail, ".pdf");
        List<String> notes = new ArrayList<>();
        if (pdfs.isEmpty() || invoices.isEmpty()) return notes;

        for (InboundMailDTO.Attachment pdf : pdfs) {
            List<InvoiceDTO> candidates = invoices.stream().filter(i -> nameMatches(pdf.name(), i)).toList();
            InvoiceDTO target;
            if (candidates.size() == 1) {
                target = candidates.get(0);
            } else if (candidates.isEmpty() && invoices.size() == 1 && !hasNumber(pdf.name())) {
                // Single invoice in the message and a name that says nothing: it is that invoice's PDF
                target = invoices.get(0);
            } else {
                notes.add(pdf.name() + ": PDF não vinculado (nome não confere com a nota)");
                continue;
            }
            try {
                invoiceService.attachPdf(target.id(), Base64.getDecoder().decode(pdf.content()));
            } catch (RuntimeException e) {
                notes.add(pdf.name() + ": " + e.getMessage());
            }
        }
        return notes;
    }

    /**
     * Whether the file name carries this invoice's number.
     *
     * Leading zeros are ignored, so "000006888-002-danfe.pdf" matches invoice 6888. The series is not looked
     * at: that "002" is the series and would match almost anything. The number may also sit inside a longer
     * block, as in the Baumer file "10020243268", where 243268 appears after a zero - that zero is required,
     * so that "000006889" cannot be read as invoice 889.
     */
    private static boolean nameMatches(String fileName, InvoiceDTO invoice) {
        if (fileName == null || invoice.number() == null) return false;
        String number = invoice.number().replaceFirst("^0+", "");
        Matcher matcher = NAME_NUMBER.matcher(fileName);
        while (matcher.find()) {
            String found = matcher.group();
            if (found.replaceFirst("^0+", "").equals(number)) return true;
            if (found.length() > number.length() && found.endsWith(number)
                    && found.charAt(found.length() - number.length() - 1) == '0') {
                return true;
            }
        }
        return false;
    }

    private static boolean hasNumber(String fileName) {
        return fileName != null && NAME_NUMBER.matcher(fileName).find();
    }

    // ============================================================ tracking

    /** A message with no XML: it may be the e-mail that says which invoices travelled under a code. */
    private void tracking(InboundMailDTO mail, IncomingEmail record) {
        ParsedTracking parsed = trackingParser.parse(mail.subject(), mail.bodyText());
        if (parsed.isEmpty()) {
            record.setStatus(IncomingEmailStatus.UNRECOGNISED);
            record.setDetail("Sem XML de nota fiscal e sem código de rastreio");
            return;
        }
        if (!parsed.isUsable()) {
            // More than one code in one message, or a code with no invoice named: nothing is matched by
            // guesswork. What was found is written down so it can be settled by hand.
            record.setStatus(IncomingEmailStatus.UNRECOGNISED);
            record.setDetail("Rastreio não aplicado automaticamente — códigos: "
                    + String.join(", ", parsed.codes()) + "; notas citadas: "
                    + (parsed.invoiceNumbers().isEmpty() ? "nenhuma" : String.join(", ", parsed.invoiceNumbers())));
            return;
        }

        TrackingResult result = trackingService.apply(parsed.codes().get(0), parsed.invoiceNumbers(), mail.sender());
        record.setStatus(IncomingEmailStatus.TRACKING);
        record.setDetail(result.describe());
    }

    // ============================================================ queries

    @Transactional(readOnly = true)
    public Page<IncomingEmailDTO> list(Pageable pageable) {
        accessControlService.requireManager();
        return repository.findAllByOrderByReadAtDescIdDesc(pageable).map(IncomingEmailDTO::of);
    }

    /** Messages that could not be used; the number the screen shows beside the mailbox log. */
    @Transactional(readOnly = true)
    public long countUnrecognised() {
        return repository.countByStatus(IncomingEmailStatus.UNRECOGNISED)
                + repository.countByStatus(IncomingEmailStatus.FAILED);
    }

    // ============================================================ helpers

    private static List<InboundMailDTO.Attachment> attachments(InboundMailDTO mail, String extension) {
        if (mail.attachments() == null) return List.of();
        return mail.attachments().stream()
                .filter(a -> a.content() != null && a.name() != null
                        && a.name().toLowerCase().endsWith(extension))
                .toList();
    }

    private static LocalDateTime dateTime(String value) {
        if (value == null) return null;
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String trim(String value, int max) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
