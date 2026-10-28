package com.project.mss.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    @Value("${broker.queue.email.name}")
    private String emailQueue;

    @Value("${broker.queue.invoice.name}")
    private String invoiceQueue;

    @Value("${broker.queue.invoice.read.name}")
    private String invoiceReadQueue;

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * Both queues are declared here as well as in the mail service.
     *
     * A queue declared by only one side exists only while that side is up: with the mail service stopped, the
     * broker drops what is published to it and answers nothing, which is how a first-access invitation was
     * lost once with the screen reporting success. Declaring from both ends costs nothing - declaring an
     * existing queue with the same settings is a no-op - and the message waits instead of vanishing.
     */
    @Bean
    public Queue emailQueue() {
        return new Queue(emailQueue, true);
    }

    /** Messages read from the invoice mailbox, published by the mail service and consumed here. */
    @Bean
    public Queue invoiceQueue() {
        return new Queue(invoiceQueue, true);
    }

    /** Requests to read the mailbox, published here and consumed by the mail service. */
    @Bean
    public Queue invoiceReadQueue() {
        return new Queue(invoiceReadQueue, true);
    }
}
