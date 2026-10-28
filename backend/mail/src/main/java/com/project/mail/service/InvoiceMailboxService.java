package com.project.mail.service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.project.mail.dto.InboundMailDTO;
import com.project.mail.producer.InboundMailProducer;

import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.FlagTerm;
import jakarta.mail.search.OrTerm;
import jakarta.mail.search.ReceivedDateTerm;
import jakarta.mail.search.SearchTerm;

/**
 * Reads the mailbox the suppliers send invoices to, and publishes each message for the mss to interpret.
 *
 * The mailbox is a dedicated account, separate from the one that sends the first-access invitations: this one
 * is opened by a robot that marks messages as read and moves them around, and the other is a sender whose
 * address users reply to. One password change should not be able to break both.
 *
 * Nothing is deleted here, ever. A message that was published is marked as read and, when a destination
 * folder is configured, moved to it - so a mistake on this side is always recoverable from the mailbox.
 */
@Service
public class InvoiceMailboxService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceMailboxService.class);

    /** Messages read in one run; a large backlog is read over several runs instead of in one long session. */
    private static final int BATCH = 50;

    /** How far back a re-read goes when asked by hand. */
    private static final int REREAD_DAYS = 30;
    /**
     * Largest attachment carried to the system, in bytes.
     *
     * An NF-e XML is a few kilobytes and a DANFE is a few hundred; ten megabytes is far above anything a
     * supplier sends. The limit is not about them: that mailbox receives whatever arrives, and a thirty
     * megabyte scan - read into memory, then base64, which adds a third - would be enough to take the mail
     * service down. Over the limit the attachment is skipped by name, and the message says so.
     */
    private static final int MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024;

    private final InboundMailProducer producer;

    @Value("${invoice.mailbox.enabled:false}")
    private boolean enabled;

    @Value("${invoice.mailbox.host:imap.gmail.com}")
    private String host;

    @Value("${invoice.mailbox.port:993}")
    private int port;

    @Value("${invoice.mailbox.username:}")
    private String username;

    @Value("${invoice.mailbox.password:}")
    private String password;

    @Value("${invoice.mailbox.folder:INBOX}")
    private String folderName;

    /** Folder the read messages are moved to; empty keeps them where they are, only marked as read. */
    @Value("${invoice.mailbox.processed-folder:}")
    private String processedFolder;

    public InvoiceMailboxService(InboundMailProducer producer) {
        this.producer = producer;
    }

    /**
     * Reads the unread messages and publishes them.
     *
     * rereadRecent also picks up messages already read in the last thirty days. That is what the "read now"
     * button asks for: when the reading improves, the messages an older version did not understand are
     * looked at again - the mss keeps the ones that produced nothing and skips the rest, so nothing is
     * registered twice. The nightly run never does this: revisiting a month of mail every dawn would cost
     * a lot to find nothing almost every time.
     */
    public int read(boolean rereadRecent) {
        if (!enabled) {
            log.info("Invoice mailbox reading is turned off (invoice.mailbox.enabled)");
            return 0;
        }
        if (username.isBlank() || password.isBlank()) {
            log.warn("Invoice mailbox without credentials: nothing read");
            return 0;
        }

        Properties properties = new Properties();
        properties.put("mail.store.protocol", "imaps");
        properties.put("mail.imaps.host", host);
        properties.put("mail.imaps.port", String.valueOf(port));
        properties.put("mail.imaps.ssl.enable", "true");
        properties.put("mail.imaps.connectiontimeout", "20000");
        properties.put("mail.imaps.timeout", "60000");

        int published = 0;
        try (Store store = Session.getInstance(properties).getStore("imaps")) {
            store.connect(host, port, username, password);
            Folder folder = store.getFolder(folderName);
            folder.open(Folder.READ_WRITE);
            try {
                Message[] messages = rereadRecent ? recent(folder) : folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
                int limit = Math.min(messages.length, BATCH);
                for (int i = 0; i < limit; i++) {
                    Message message = messages[i];
                    try {
                        producer.publish(toDto(folder, message));
                        message.setFlag(Flags.Flag.SEEN, true);
                        published++;
                    } catch (Exception e) {
                        // Left unread on purpose: an unread message comes back on the next run, a message
                        // marked as read after a failure would be lost without anyone noticing
                        log.warn("Could not publish a message from the invoice mailbox: {}", e.getMessage());
                    }
                }
                moveProcessed(store, folder, messages, limit);
            } finally {
                folder.close(false);
            }
        } catch (Exception e) {
            log.error("Could not read the invoice mailbox: {}", e.getMessage(), e);
        }
        log.info("Invoice mailbox: {} message(s) published", published);
        return published;
    }

    /** Unread messages plus everything received in the last thirty days, whether read or not. */
    private Message[] recent(Folder folder) throws Exception {
        java.util.Date since = java.util.Date.from(java.time.Instant.now().minus(REREAD_DAYS, java.time.temporal.ChronoUnit.DAYS));
        SearchTerm term = new OrTerm(
                new FlagTerm(new Flags(Flags.Flag.SEEN), false),
                new ReceivedDateTerm(ComparisonTerm.GE, since));
        return folder.search(term);
    }

    /** Moves the messages just read into the configured folder, when there is one. */
    private void moveProcessed(Store store, Folder source, Message[] messages, int limit) {
        if (processedFolder.isBlank() || limit == 0) return;
        try {
            Folder destination = store.getFolder(processedFolder);
            if (!destination.exists()) destination.create(Folder.HOLDS_MESSAGES);
            Message[] read = new Message[limit];
            System.arraycopy(messages, 0, read, 0, limit);
            source.copyMessages(read, destination);
        } catch (Exception e) {
            // The message was already published; failing to tidy the mailbox is not worth losing the run over
            log.warn("Messages published but not moved to {}: {}", processedFolder, e.getMessage());
        }
    }

    private InboundMailDTO toDto(Folder folder, Message message) throws Exception {
        List<InboundMailDTO.Attachment> attachments = new ArrayList<>();
        collect(message, attachments);
        String uid = messageUid(folder, message);
        String sender = message.getFrom() == null || message.getFrom().length == 0
                ? null : message.getFrom()[0].toString();
        String sentAt = message.getSentDate() == null ? null
                : LocalDateTime.ofInstant(message.getSentDate().toInstant(), ZoneId.systemDefault())
                        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return new InboundMailDTO(uid, sender, message.getSubject(), sentAt, bodyText(message), attachments);
    }

    /**
     * Stable identifier of the message, so the mss can tell a message it has already seen. The IMAP UID is
     * unique within the folder; the Message-ID header is the fallback when the folder has no UIDs.
     */
    private String messageUid(Folder folder, Message message) throws Exception {
        if (folder instanceof jakarta.mail.UIDFolder uidFolder) {
            return folderName + ":" + uidFolder.getUID(message);
        }
        String[] header = message.getHeader("Message-ID");
        return header != null && header.length > 0 ? header[0] : folderName + ":" + message.getMessageNumber();
    }

    /** Every attachment of the message, whatever its nesting; forwarded e-mails bury them one level deeper. */
    private void collect(Part part, List<InboundMailDTO.Attachment> attachments) throws Exception {
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                collect(multipart.getBodyPart(i), attachments);
            }
            return;
        }
        if (part.isMimeType("message/rfc822")) {
            collect((MimeMessage) part.getContent(), attachments);
            return;
        }
        String fileName = part.getFileName();
        boolean attached = Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()) || fileName != null;
        if (!attached || fileName == null) return;
        // The declared size first, which costs nothing and stops the usual case before a byte is read
        int declared = part.getSize();
        if (declared > MAX_ATTACHMENT_BYTES) {
            log.warn("Attachment {} skipped: {} bytes, over the {} byte limit", fileName, declared, MAX_ATTACHMENT_BYTES);
            return;
        }
        try (InputStream input = part.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) > 0) {
                if (output.size() + read > MAX_ATTACHMENT_BYTES) {
                    // A part that lies about its size, or does not declare one: stop before the heap pays for it
                    log.warn("Attachment {} skipped: over the {} byte limit", fileName, MAX_ATTACHMENT_BYTES);
                    return;
                }
                output.write(buffer, 0, read);
            }
            attachments.add(new InboundMailDTO.Attachment(decode(fileName),
                    Base64.getEncoder().encodeToString(output.toByteArray())));
        }
    }

    /**
     * Plain text of the message, when it has one. It is forwarded untouched: the tracking e-mail will be read
     * from it, and until there is a real example of that message nothing here tries to guess its shape.
     */
    private String bodyText(Part part) {
        try {
            if (part.isMimeType("text/plain")) return (String) part.getContent();
            if (part.isMimeType("multipart/*")) {
                Multipart multipart = (Multipart) part.getContent();
                for (int i = 0; i < multipart.getCount(); i++) {
                    String text = bodyText(multipart.getBodyPart(i));
                    if (text != null) return text;
                }
            }
        } catch (Exception e) {
            log.debug("Could not read the message body: {}", e.getMessage());
        }
        return null;
    }

    /** File names arrive MIME-encoded when they carry accents. */
    private static String decode(String fileName) {
        try {
            return jakarta.mail.internet.MimeUtility.decodeText(fileName);
        } catch (Exception e) {
            return fileName;
        }
    }
}
