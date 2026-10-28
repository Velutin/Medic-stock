package com.project.mss.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.project.mss.dto.invoice.IncomingEmailDTO;
import com.project.mss.dto.invoice.InvoiceDTO;
import com.project.mss.dto.invoice.InvoiceImportResultDTO;
import com.project.mss.dto.invoice.PendingTrackingDTO;
import com.project.mss.exception.BusinessRuleException;
import com.project.mss.service.AppSettingService;
import com.project.mss.service.IncomingEmailService;
import com.project.mss.service.InvoiceReadingScheduler;
import com.project.mss.service.InvoiceTrackingService;
import com.project.mss.service.SupplierInvoiceService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/invoices")
@Tag(name = "Supplier invoices", description = "Material announced by the supplier and still on its way (ADMIN)")
public class SupplierInvoiceController {

    private final SupplierInvoiceService invoiceService;
    private final IncomingEmailService incomingEmailService;
    private final AppSettingService settingService;
    private final InvoiceReadingScheduler readingScheduler;
    private final InvoiceTrackingService trackingService;

    public SupplierInvoiceController(SupplierInvoiceService invoiceService,
                                     IncomingEmailService incomingEmailService,
                                     AppSettingService settingService,
                                     InvoiceReadingScheduler readingScheduler,
                                     InvoiceTrackingService trackingService) {
        this.invoiceService = invoiceService;
        this.incomingEmailService = incomingEmailService;
        this.settingService = settingService;
        this.readingScheduler = readingScheduler;
        this.trackingService = trackingService;
    }

    @GetMapping
    @Operation(summary = "Invoices, newest first",
               description = "status: OPEN (everything still waiting for material), PARTIAL, RECEIVED, CLOSED, "
                       + "NOT_SHIPMENT, AWAITING_XML or ALL. search matches the number, the tracking code, the "
                       + "emitter and the lots the invoice declares. category (IMPLANT or INSTRUMENT) keeps only "
                       + "the invoices that carry that kind of item.")
    public Page<InvoiceDTO> list(@RequestParam(required = false) String status,
                                 @RequestParam(required = false) String search,
                                 @RequestParam(required = false) String category,
                                 @ParameterObject @PageableDefault(size = 10) Pageable pageable) {
        return invoiceService.list(status, search, category, pageable);
    }

    @GetMapping("/overdue")
    @Operation(summary = "Invoices open for longer than the configured number of days",
               description = "The material that left the supplier and did not arrive: what to charge them for.")
    public List<InvoiceDTO> overdue() {
        return invoiceService.overdue();
    }

    @GetMapping("/{id}")
    public InvoiceDTO find(@PathVariable Long id) {
        return invoiceService.find(id);
    }

    @PostMapping(value = "/xml", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import an NF-e XML by hand",
               description = "Same reading as the mailbox. Answers with the outcome: IMPORTED, DUPLICATE or "
                       + "NOT_SHIPMENT (a return, an unknown emitter or a CFOP outside the shipment list).")
    public InvoiceImportResultDTO upload(@RequestPart("file") MultipartFile file) {
        try {
            return invoiceService.upload(file.getBytes());
        } catch (IOException e) {
            throw new BusinessRuleException("Could not read the NF-e XML");
        }
    }

    @GetMapping("/{id}/pdf")
    @Operation(summary = "Download the DANFE stored with the invoice",
               description = "Available while the file is within the retention period; after that the invoice "
                       + "stays and only the file is gone.")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        byte[] pdf = invoiceService.pdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"nota-" + id + ".pdf\"")
                .body(pdf);
    }

    @PatchMapping("/{id}/tracking-code")
    @Operation(summary = "Set or clear the tracking code of an invoice")
    public InvoiceDTO setTrackingCode(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return invoiceService.setTrackingCode(id, body.get("trackingCode"));
    }

    @PatchMapping("/{id}/closure")
    @Operation(summary = "Close an invoice that will not be received, or reopen one closed by mistake",
               description = "Closing requires a reason; reopening sends reopen = true.")
    public InvoiceDTO closure(@PathVariable Long id, @RequestBody Map<String, String> body) {
        if ("true".equalsIgnoreCase(body.get("reopen"))) return invoiceService.reopen(id);
        return invoiceService.close(id, body.get("reason"));
    }

    @GetMapping("/by-key/{key}")
    @Operation(summary = "Invoice of a scanned DANFE access key",
               description = "Answers 204 when the key is valid but its XML has not been read from the "
                       + "mailbox yet. The entry uses it to say so while the box is still open, instead of "
                       + "letting the person find out when the lots turn into pending issues.")
    public ResponseEntity<InvoiceDTO> byKey(@PathVariable String key) {
        return supplierInvoiceService.byAccessKey(key)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/mailbox")
    @Operation(summary = "Messages read from the invoice mailbox, newest first",
               description = "Includes the ones with nothing usable inside, with the reason.")
    public Page<IncomingEmailDTO> mailbox(@ParameterObject @PageableDefault(size = 10) Pageable pageable) {
        return incomingEmailService.list(pageable);
    }

    @PostMapping("/mailbox/readings")
    @Operation(summary = "Read the mailbox now",
               description = "Asks the mail service to read it. The outcome shows up in the mailbox log, "
                       + "message by message, as each one is read.")
    public void readNow() {
        readingScheduler.readNow();
    }

    @GetMapping("/pending-tracking")
    @Operation(summary = "Tracking codes waiting for an invoice that has not been read yet",
               description = "A code that stays here means the carrier e-mail announced an invoice whose XML "
                       + "never arrived.")
    public List<PendingTrackingDTO> pendingTracking() {
        return trackingService.waiting();
    }

    @DeleteMapping("/pending-tracking/{id}")
    @Operation(summary = "Discard a tracking code that will never find its invoice",
               description = "For invoices issued before the mailbox existed: their XML is not coming, and a "
                       + "list of things waiting forever is a list nobody reads.")
    public void discardPendingTracking(@PathVariable Long id) {
        trackingService.discard(id);
    }

    @GetMapping("/settings")
    @Operation(summary = "Reading hour, alert days and whether the entry requires an invoice")
    public Map<String, String> settings() {
        return settingService.all();
    }

    @PutMapping("/settings")
    @Operation(summary = "Change the settings above")
    public Map<String, String> updateSettings(@RequestBody Map<String, String> changes) {
        return settingService.update(changes);
    }
}
