package com.project.mss.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.mss.model.entity.SupplierInvoice;
import com.project.mss.repository.SupplierInvoiceRepository;

/**
 * Deletes invoice PDFs older than the retention period.
 *
 * Only the file goes. The invoice, with every fact the XML carried, stays forever: what costs space is the
 * attachment, and losing the record to save a few megabytes would be a bad trade. The invoice keeps the date
 * the file was deleted, so the screen can say "expired" rather than "never had one" - the difference matters
 * when someone goes looking for a document from eight months ago.
 */
@Service
public class InvoicePdfRetentionService {

    private static final Logger log = LoggerFactory.getLogger(InvoicePdfRetentionService.class);

    private final SupplierInvoiceRepository invoiceRepository;
    private final FileStorageService fileStorageService;
    private final AppSettingService settingService;

    public InvoicePdfRetentionService(SupplierInvoiceRepository invoiceRepository,
                                      FileStorageService fileStorageService, AppSettingService settingService) {
        this.invoiceRepository = invoiceRepository;
        this.fileStorageService = fileStorageService;
        this.settingService = settingService;
    }

    /** Early morning, after the backup has already copied the folder for the night. */
    @Scheduled(cron = "0 40 4 * * *")
    @Transactional
    public void deleteExpiredPdfs() {
        int days = settingService.pdfRetentionDays();
        LocalDateTime limit = LocalDateTime.now().minusDays(days);
        List<SupplierInvoice> expired = invoiceRepository.findByPdfFileIsNotNullAndPdfStoredAtBefore(limit);
        if (expired.isEmpty()) return;

        int deleted = 0;
        for (SupplierInvoice invoice : expired) {
            fileStorageService.delete(invoice.getPdfFile());
            invoice.setPdfFile(null);
            invoice.setPdfExpiredAt(LocalDateTime.now());
            invoiceRepository.save(invoice);
            deleted++;
        }
        log.info("Invoice PDFs older than {} days deleted: {}", days, deleted);
    }
}
