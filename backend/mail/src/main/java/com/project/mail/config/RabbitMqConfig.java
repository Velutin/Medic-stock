package com.project.mail.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    @Value("${broker.queue.email.name}")
    private String queue;

    @Value("${broker.queue.invoice.name}")
    private String invoiceQueue;

    @Value("${broker.queue.invoice.read.name}")
    private String invoiceReadQueue;

    @Bean
    public Queue queue() {
        return new Queue(queue, true);
    }

    /** Messages read from the invoice mailbox, published here and consumed by the mss. */
    @Bean
    public Queue invoiceQueue() {
        return new Queue(invoiceQueue, true);
    }

    /** Requests from the mss to read the mailbox now. */
    @Bean
    public Queue invoiceReadQueue() {
        return new Queue(invoiceReadQueue, true);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
