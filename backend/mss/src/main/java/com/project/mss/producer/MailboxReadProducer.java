package com.project.mss.producer;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Asks the mail service to read the invoice mailbox now. */
@Component
public class MailboxReadProducer {

    private final RabbitTemplate rabbitTemplate;

    @Value("${broker.queue.invoice.read.name}")
    private String routingKey;

    public MailboxReadProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /** reason is what shows up in the mail service log: the schedule, or the administrator. */
    public void requestReading(String reason) {
        rabbitTemplate.convertAndSend("", routingKey, reason);
    }
}
