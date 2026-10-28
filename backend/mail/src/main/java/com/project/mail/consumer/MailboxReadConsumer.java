package com.project.mail.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.project.mail.service.InvoiceMailboxService;

/**
 * A request to read the invoice mailbox, sent by the mss.
 *
 * The reading hour lives in the mss, where the administrator edits it on a screen; the mailbox credentials
 * live here. So the mss decides when and this service does it: the setting stays editable without a release,
 * and neither service keeps a copy of the other's configuration.
 */
@Component
public class MailboxReadConsumer {

    private static final Logger log = LoggerFactory.getLogger(MailboxReadConsumer.class);

    private final InvoiceMailboxService mailboxService;

    public MailboxReadConsumer(InvoiceMailboxService mailboxService) {
        this.mailboxService = mailboxService;
    }

    /**
     * The reason also says how wide the reading is. A request made by hand re-reads the last thirty days as
     * well, so a message an older reader did not understand gets another chance; the daily schedule stays
     * with the unread ones.
     */
    @RabbitListener(queues = "${broker.queue.invoice.read.name}")
    public void listenReadRequests(String reason) {
        boolean rereadRecent = reason != null && reason.startsWith("requested by");
        log.info("Reading the invoice mailbox ({}{})", reason, rereadRecent ? ", including the last 30 days" : "");
        mailboxService.read(rereadRecent);
    }
}
