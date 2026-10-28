package com.project.mss.service;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.project.mss.exception.BusinessRuleException;
import com.project.mss.util.NfeAccessKey;

/**
 * Reads an NF-e XML into the few facts the stock control needs.
 *
 * Three things in the real files drive the shape of what comes out:
 *
 *  - the quantity that matters is qCom, the quantity shipped. The rastro blocks declare, per lot, the size of
 *    its sterilisation group, which is often larger than the shipment (8 units against lots of 1 and 14), so
 *    they are read as candidate lots and never as a division of the shipment;
 *  - the recipient is not always us. On an OPME shipment the invoice is issued to the hospital and only the
 *    delivery address is ours, so nothing here filters by recipient;
 *  - what is coming to us is told by tpNF = 1 (an issue, not a return) together with the CFOP. The operation
 *    description is free text and is kept for the screen, never used as a rule.
 *
 * Patient and surgeon, which OPME invoices carry in the complementary information, are deliberately not read.
 */
@Component
public class NfeXmlParser {

    /** "Data da cirurgia: 24/10/26" in the complementary information; the year may have two or four digits. */
    private static final Pattern SURGERY_DATE =
            Pattern.compile("Data da cirurgia\\s*:\\s*(\\d{2})/(\\d{2})/(\\d{2,4})", Pattern.CASE_INSENSITIVE);

    /** What one XML says. Items are in invoice order; nothing is grouped by REF. */
    public record ParsedInvoice(String accessKey, String emitterCnpj, String emitterName, String number,
                                String series, LocalDateTime issuedAt, String operation, String cfop,
                                boolean issuedByEmitter, String recipientName, String recipientCnpj,
                                String carrierName, String carrierCnpj, String trackingCode, LocalDate surgeryDate,
                                BigDecimal totalValue, List<ParsedItem> items) { }

    public record ParsedItem(int itemNumber, String productCode, String gtin, String description, String cfop,
                             int quantity, BigDecimal unitValue, List<ParsedLot> lots) { }

    public record ParsedLot(String number, LocalDate manufacturedAt, LocalDate expiryDate, Integer quantity) { }

    public ParsedInvoice parse(byte[] xml) {
        Document document = read(xml);
        Element infNFe = first(document, "infNFe");
        if (infNFe == null) throw new BusinessRuleException("The file is not an NF-e XML");

        String accessKey = accessKey(document, infNFe);
        Element ide = first(infNFe, "ide");
        Element emit = first(infNFe, "emit");
        Element dest = first(infNFe, "dest");
        Element transp = first(infNFe, "transp");
        Element transporta = transp == null ? null : first(transp, "transporta");
        Element vol = transp == null ? null : first(transp, "vol");

        List<ParsedItem> items = new ArrayList<>();
        NodeList details = infNFe.getElementsByTagName("det");
        for (int i = 0; i < details.getLength(); i++) {
            Element det = (Element) details.item(i);
            Element prod = first(det, "prod");
            if (prod == null) continue;
            items.add(item(det, prod, i + 1));
        }

        String complementary = text(first(infNFe, "infCpl"));
        return new ParsedInvoice(
                accessKey,
                digits(text(first(emit, "CNPJ"))),
                text(first(emit, "xNome")),
                text(first(ide, "nNF")),
                text(first(ide, "serie")),
                dateTime(text(first(ide, "dhEmi"))),
                text(first(ide, "natOp")),
                items.isEmpty() ? null : items.get(0).cfop(),
                "1".equals(text(first(ide, "tpNF"))),
                text(first(dest, "xNome")),
                digits(firstOf(dest, "CNPJ", "CPF")),
                transporta == null ? null : text(first(transporta, "xNome")),
                transporta == null ? null : digits(firstOf(transporta, "CNPJ", "CPF")),
                // Some issuers write the tracking code in the volume number; when they do not, it comes later
                // from the tracking e-mail and is filled in there.
                vol == null ? null : trimToNull(text(first(vol, "nVol"))),
                surgeryDate(complementary),
                decimal(text(first(first(infNFe, "ICMSTot"), "vNF"))),
                items);
    }

    // ============================================================ helpers

    private ParsedItem item(Element det, Element prod, int fallbackNumber) {
        int itemNumber = fallbackNumber;
        String attribute = det.getAttribute("nItem");
        if (attribute != null && attribute.matches("\\d+")) itemNumber = Integer.parseInt(attribute);

        List<ParsedLot> lots = new ArrayList<>();
        NodeList rastro = prod.getElementsByTagName("rastro");
        for (int i = 0; i < rastro.getLength(); i++) {
            Element lot = (Element) rastro.item(i);
            lots.add(new ParsedLot(text(first(lot, "nLote")), date(text(first(lot, "dFab"))),
                    date(text(first(lot, "dVal"))), integer(text(first(lot, "qLote")))));
        }

        BigDecimal quantity = decimal(text(first(prod, "qCom")));
        return new ParsedItem(itemNumber, text(first(prod, "cProd")), gtin(text(first(prod, "cEAN"))),
                text(first(prod, "xProd")), text(first(prod, "CFOP")),
                quantity == null ? 0 : quantity.setScale(0, java.math.RoundingMode.HALF_UP).intValue(),
                decimal(text(first(prod, "vUnCom"))), lots);
    }

    /** Key from the Id attribute ("NFe" + 44 digits), falling back to the authorisation protocol. */
    private String accessKey(Document document, Element infNFe) {
        NfeAccessKey key = NfeAccessKey.parse(infNFe.getAttribute("Id"));
        if (key == null) key = NfeAccessKey.parse(text(first(document, "chNFe")));
        if (key == null) throw new BusinessRuleException("The NF-e XML has no valid access key");
        return key.digits();
    }

    /** XML reader with external entities disabled: the file comes from an e-mail box. */
    private Document read(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            // Namespace-unaware on purpose: every tag in the NF-e comes unprefixed, and the parsing is by name
            factory.setNamespaceAware(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(xml));
        } catch (Exception e) {
            throw new BusinessRuleException("Could not read the NF-e XML");
        }
    }

    private static Element first(Node scope, String tag) {
        if (scope == null) return null;
        NodeList nodes = scope instanceof Document document
                ? document.getElementsByTagName(tag)
                : ((Element) scope).getElementsByTagName(tag);
        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }

    private static String firstOf(Node scope, String... tags) {
        for (String tag : tags) {
            String value = text(first(scope, tag));
            if (value != null) return value;
        }
        return null;
    }

    private static String text(Element element) {
        if (element == null) return null;
        String content = element.getTextContent();
        return trimToNull(content);
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String digits(String value) {
        if (value == null) return null;
        String only = value.replaceAll("\\D", "");
        return only.isEmpty() ? null : only;
    }

    /** GTIN stored with 14 digits, as the catalog keeps it; "SEM GTIN" and the like become null. */
    private static String gtin(String value) {
        String only = digits(value);
        if (only == null || only.length() > 14) return null;
        return "0".repeat(14 - only.length()) + only;
    }

    private static LocalDateTime dateTime(String value) {
        if (value == null) return null;
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private static LocalDate date(String value) {
        if (value == null) return null;
        try {
            return LocalDate.parse(value.substring(0, 10));
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer integer(String value) {
        BigDecimal decimal = decimal(value);
        return decimal == null ? null : decimal.setScale(0, java.math.RoundingMode.HALF_UP).intValue();
    }

    private static BigDecimal decimal(String value) {
        if (value == null) return null;
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Surgery date from the complementary information; the patient and the surgeon beside it are ignored. */
    private static LocalDate surgeryDate(String complementary) {
        if (complementary == null) return null;
        Matcher matcher = SURGERY_DATE.matcher(complementary);
        if (!matcher.find()) return null;
        try {
            int year = Integer.parseInt(matcher.group(3));
            if (year < 100) year += 2000;
            return LocalDate.of(year, Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(1)));
        } catch (Exception e) {
            return null;
        }
    }

    /** Convenience for callers holding the XML as text. */
    public ParsedInvoice parse(String xml) {
        return parse(xml.getBytes(StandardCharsets.UTF_8));
    }
}
