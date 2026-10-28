package com.project.mss.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.mss.exception.BusinessRuleException;
import com.project.mss.model.entity.AppSetting;
import com.project.mss.repository.AppSettingRepository;

/**
 * Settings the administrator changes from the screen.
 *
 * Reads fall back to the default when the row is missing or holds something unreadable: a bad value in a
 * settings table must not be able to stop the mailbox from being read.
 */
@Service
public class AppSettingService {

    private static final int DEFAULT_READING_HOUR = 3;
    private static final int DEFAULT_ALERT_DAYS = 7;
    private static final int DEFAULT_PDF_RETENTION_DAYS = 180;

    private final AppSettingRepository repository;
    private final AccessControlService accessControlService;

    public AppSettingService(AppSettingRepository repository, AccessControlService accessControlService) {
        this.repository = repository;
        this.accessControlService = accessControlService;
    }

    @Transactional(readOnly = true)
    public Map<String, String> all() {
        accessControlService.requireManager();
        Map<String, String> values = new LinkedHashMap<>();
        values.put(AppSetting.INVOICE_READING_HOUR, String.valueOf(readingHour()));
        values.put(AppSetting.INVOICE_ALERT_DAYS, String.valueOf(alertDays()));
        values.put(AppSetting.INVOICE_REQUIRED_ON_ENTRY, String.valueOf(invoiceRequiredOnEntry()));
        values.put(AppSetting.INVOICE_PDF_RETENTION_DAYS, String.valueOf(pdfRetentionDays()));
        return values;
    }

    @Transactional
    public Map<String, String> update(Map<String, String> changes) {
        accessControlService.requireManager();
        changes.forEach((key, value) -> {
            switch (key) {
                case AppSetting.INVOICE_READING_HOUR -> {
                    int hour = number(value, "Reading hour must be a whole number between 0 and 23");
                    if (hour < 0 || hour > 23) {
                        throw new BusinessRuleException("Reading hour must be a whole number between 0 and 23");
                    }
                }
                case AppSetting.INVOICE_ALERT_DAYS -> {
                    int days = number(value, "Alert days must be a whole number between 1 and 90");
                    if (days < 1 || days > 90) {
                        throw new BusinessRuleException("Alert days must be a whole number between 1 and 90");
                    }
                }
                case AppSetting.INVOICE_PDF_RETENTION_DAYS -> {
                    int days = number(value, "PDF retention must be a whole number between 30 and 1825 days");
                    if (days < 30 || days > 1825) {
                        throw new BusinessRuleException("PDF retention must be a whole number between 30 and 1825 days");
                    }
                }
                case AppSetting.INVOICE_REQUIRED_ON_ENTRY -> {
                    if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                        throw new BusinessRuleException("Setting " + key + " accepts only true or false");
                    }
                }
                default -> throw new BusinessRuleException("Unknown setting: " + key);
            }
            AppSetting setting = repository.findById(key).orElseGet(() -> new AppSetting(key, value));
            setting.setValue(value.trim().toLowerCase());
            setting.setUpdatedBy(accessControlService.currentUser());
            setting.setUpdatedAt(LocalDateTime.now());
            repository.save(setting);
        });
        return all();
    }

    @Transactional(readOnly = true)
    public int readingHour() {
        return integer(AppSetting.INVOICE_READING_HOUR, DEFAULT_READING_HOUR, 0, 23);
    }

    @Transactional(readOnly = true)
    public int alertDays() {
        return integer(AppSetting.INVOICE_ALERT_DAYS, DEFAULT_ALERT_DAYS, 1, 90);
    }

    /** Days an invoice PDF is kept before the nightly job deletes the file (the invoice itself stays). */
    @Transactional(readOnly = true)
    public int pdfRetentionDays() {
        return integer(AppSetting.INVOICE_PDF_RETENTION_DAYS, DEFAULT_PDF_RETENTION_DAYS, 30, 1825);
    }

    @Transactional(readOnly = true)
    public boolean invoiceRequiredOnEntry() {
        return repository.findById(AppSetting.INVOICE_REQUIRED_ON_ENTRY)
                .map(s -> "true".equalsIgnoreCase(s.getValue()))
                .orElse(false);
    }

    private int integer(String key, int fallback, int min, int max) {
        try {
            int value = repository.findById(key).map(s -> Integer.parseInt(s.getValue().trim())).orElse(fallback);
            return value < min || value > max ? fallback : value;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int number(String value, String message) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException e) {
            throw new BusinessRuleException(message);
        }
    }
}
