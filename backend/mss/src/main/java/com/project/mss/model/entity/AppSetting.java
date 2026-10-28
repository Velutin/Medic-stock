package com.project.mss.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A value the administrator changes from the screen: the hour the mailbox is read, how many days an invoice
 * may stay open before it is counted as not received, and whether an entry without an invoice is refused.
 *
 * These are settings and not constants because each of them is a decision that changes with use, and a
 * decision that needs a release to change is a decision nobody revisits.
 */
@Entity
@Table(name = "app_setting")
@Getter
@Setter
@NoArgsConstructor
public class AppSetting {

    /** Reading hour of the mailbox, 0-23, server time. */
    public static final String INVOICE_READING_HOUR = "invoice.reading.hour";

    /** Days an invoice may stay open before it is counted as not received. */
    public static final String INVOICE_ALERT_DAYS = "invoice.alert.days";

    /** True: an entry with no linked invoice is refused (turned on once the reading is settled). */
    public static final String INVOICE_REQUIRED_ON_ENTRY = "invoice.required.on.entry";

    /** Days the DANFE of an invoice is kept on disk; the invoice itself is never deleted. */
    public static final String INVOICE_PDF_RETENTION_DAYS = "invoice.pdf.retention.days";

    @Id
    @Column(name = "key", length = 60)
    private String key;

    @Column(nullable = false, length = 255)
    private String value;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public AppSetting(String key, String value) {
        this.key = key;
        this.value = value;
    }
}
