package com.project.mss.dto.invoice;

import java.util.List;

/**
 * A message the mail service read from the invoice mailbox, on its way to be processed here.
 *
 * It travels as plain text and base64 so nothing depends on date or binary conventions agreeing between the
 * two services. The mail service reads and forwards; it does not interpret: deciding what an attachment means
 * belongs with the invoices, which live here.
 */
public record InboundMailDTO(String messageUid, String sender, String subject, String sentAt,
                             String bodyText, List<Attachment> attachments) {

    /** content is the file in base64; name keeps the original file name, which says whether it is an XML. */
    public record Attachment(String name, String content) { }
}
