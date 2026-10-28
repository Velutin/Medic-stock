package com.project.mss.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.project.mss.model.entity.Lot;

/**
 * Reads the e-mail that announces which invoices travelled under a tracking code.
 *
 * Written against a real message:
 *
 * <pre>
 *   Assunto: Rastreio SEDEX - NF(s) 6886 - 6887 - 6888 - 6889 - 6890 - 6891
 *   Corpo:   Seguem os códigos de rastreio das seguintes notas fiscais: 6886 - 6887 - ...
 *            AE077755158BR
 *            SOCIEDADE BENEF ISRAELITABRAS HOSPITAL ALBERT EINSTEIN
 * </pre>
 *
 * Two things that message taught, and that the reading depends on:
 *
 *  - invoice numbers are only looked for on lines that mention invoices ("NF(s)", "notas fiscais"). A loose
 *    search for numbers would pick up the CEP and the CNPJ further down and turn them into invoices;
 *  - the sender says nothing about who issued the invoices. That message came from a baumer.com.br address
 *    and listed invoices issued by Implus, so the emitter is never taken from the e-mail address.
 */
@Component
public class TrackingEmailParser {

    /** SEDEX and registered mail: two letters, nine digits, two letters. */
    private static final Pattern CODE = Pattern.compile("\\b[A-Z]{2}\\d{9}[A-Z]{2}\\b");

    /** A line that is talking about invoices. */
    private static final Pattern INVOICE_LINE =
            Pattern.compile("(nota[s]?\\s+fiscai?s?|NF\\(s\\)|\\bNFs?\\b)", Pattern.CASE_INSENSITIVE);

    private static final Pattern NUMBER = Pattern.compile("\\b\\d{1,9}\\b");

    /** What a tracking e-mail says: the codes it carries and the invoice numbers it names. */
    public record ParsedTracking(List<String> codes, List<String> invoiceNumbers) {

        /** Usable when exactly one code was found: then every invoice named travelled under it. */
        public boolean isUsable() {
            return codes.size() == 1 && !invoiceNumbers.isEmpty();
        }

        public boolean isEmpty() {
            return codes.isEmpty() && invoiceNumbers.isEmpty();
        }
    }

    public ParsedTracking parse(String subject, String body) {
        Set<String> codes = new LinkedHashSet<>();
        addCodes(subject, codes);
        addCodes(body, codes);

        List<String> numbers = new ArrayList<>();
        addNumbers(subject, numbers);
        if (body != null) {
            for (String line : body.split("\\R")) {
                addNumbers(line, numbers);
            }
        }
        return new ParsedTracking(List.copyOf(codes), numbers);
    }

    private static void addCodes(String text, Set<String> codes) {
        if (text == null) return;
        Matcher matcher = CODE.matcher(text.toUpperCase());
        while (matcher.find()) codes.add(matcher.group());
    }

    /**
     * Invoice numbers of one line, only when the line is about invoices. The tracking code is removed first,
     * so its nine digits are not read as an invoice number.
     */
    private static void addNumbers(String line, List<String> numbers) {
        if (line == null || !INVOICE_LINE.matcher(line).find()) return;
        String cleaned = CODE.matcher(line.toUpperCase()).replaceAll(" ");
        Matcher matcher = NUMBER.matcher(cleaned);
        while (matcher.find()) {
            // Numbers are compared without leading zeros, the same way lot numbers are
            String number = Lot.comparableNumber(matcher.group());
            if (!numbers.contains(number)) numbers.add(number);
        }
    }
}
