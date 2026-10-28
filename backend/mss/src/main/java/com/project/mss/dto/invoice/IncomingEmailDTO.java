package com.project.mss.dto.invoice;

import java.time.LocalDateTime;

import com.project.mss.model.entity.IncomingEmail;
import com.project.mss.model.enums.IncomingEmailStatus;

/** A message read from the invoice mailbox, as the screen lists it. */
public record IncomingEmailDTO(Long id, String sender, String subject, LocalDateTime sentAt, LocalDateTime readAt,
                               IncomingEmailStatus status, int attachments, int invoices, String detail) {

    public static IncomingEmailDTO of(IncomingEmail email) {
        return new IncomingEmailDTO(email.getId(), email.getSender(), email.getSubject(), email.getSentAt(),
                email.getReadAt(), email.getStatus(), email.getAttachments(), email.getInvoices(), email.getDetail());
    }
}
