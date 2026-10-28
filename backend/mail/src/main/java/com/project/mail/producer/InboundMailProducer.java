package com.project.mail.producer;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.project.mail.dto.InboundMailDTO;

/** Publishes the messages read from the invoice mailbox for the mss to interpret. */
@Component
public class InboundMailProducer {

    private final RabbitTemplate rabbitTemplate;

    @Value("${broker.queue.invoice.name}")
    private String routingKey;

    public InboundMailProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(InboundMailDTO mail) {
        rabbitTemplate.convertAndSend("", routingKey, mail);
    }
}
