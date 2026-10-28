package com.project.mail.dto;

import java.util.List;

/**
 * A message read from the invoice mailbox, published for the mss to interpret.
 *
 * This service reads and forwards; it does not interpret. What an attachment means - an invoice, a return, a
 * duplicate - belongs with the invoices, which live in the mss. Here the only job is to get the bytes out of
 * the mailbox without losing them.
 */
public record InboundMailDTO(String messageUid, String sender, String subject, String sentAt,
                             String bodyText, List<Attachment> attachments) {

    public record Attachment(String name, String content) { }
}
