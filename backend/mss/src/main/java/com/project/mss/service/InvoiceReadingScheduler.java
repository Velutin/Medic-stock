package com.project.mss.service;

import java.time.LocalTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.project.mss.producer.MailboxReadProducer;

/**
 * Asks for the invoice mailbox to be read once a day, at the configured hour.
 *
 * The check runs every hour and compares with the setting instead of being a fixed cron expression, because
 * the hour is a value the administrator changes on the screen, and a cron expression would only change with
 * a new release - which is the same as not being configurable at all.
 */
@Service
public class InvoiceReadingScheduler {

    private static final Logger log = LoggerFactory.getLogger(InvoiceReadingScheduler.class);

    private final AppSettingService settingService;
    private final MailboxReadProducer producer;
    private final AccessControlService accessControlService;

    public InvoiceReadingScheduler(AppSettingService settingService, MailboxReadProducer producer,
                                   AccessControlService accessControlService) {
        this.settingService = settingService;
        this.producer = producer;
        this.accessControlService = accessControlService;
    }

    /** Ten minutes past every hour, so it does not land together with every other scheduled job. */
    @Scheduled(cron = "0 10 * * * *")
    public void readingSchedule() {
        int hour = settingService.readingHour();
        if (LocalTime.now().getHour() != hour) return;
        try {
            producer.requestReading("daily schedule");
        } catch (RuntimeException e) {
            // The queue being unreachable must not take the application down; the next hour tries again
            log.error("Could not ask for the mailbox to be read: {}", e.getMessage());
        }
    }

    /** "Read now", from the screen. Answers nothing: the result shows up in the mailbox log as it is read. */
    public void readNow() {
        accessControlService.requireManager();
        producer.requestReading("requested by " + accessControlService.currentUser().getEmail());
    }
}
