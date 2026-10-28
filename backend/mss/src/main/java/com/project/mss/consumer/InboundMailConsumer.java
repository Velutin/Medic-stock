package com.project.mss.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.project.mss.dto.invoice.InboundMailDTO;
import com.project.mss.service.IncomingEmailService;

/**
 * Messages read from the invoice mailbox by the mail service.
 *
 * The exception is swallowed on purpose: a message that cannot be processed must not go back to the queue to
 * be retried forever. What it leaves behind is a line in the log with its identifier, and the message itself
 * is still in the mailbox to be looked at.
 */
@Component
public class InboundMailConsumer {

    private static final Logger log = LoggerFactory.getLogger(InboundMailConsumer.class);

    private final IncomingEmailService incomingEmailService;

    public InboundMailConsumer(IncomingEmailService incomingEmailService) {
        this.incomingEmailService = incomingEmailService;
    }

    @RabbitListener(queues = "${broker.queue.invoice.name}")
    public void listenInvoiceQueue(@Payload InboundMailDTO mail) {
        try {
            incomingEmailService.process(mail);
        } catch (RuntimeException e) {
            log.error("Could not process message {} from the invoice mailbox: {}",
                    mail == null ? "?" : mail.messageUid(), e.getMessage(), e);
        }
    }
}
