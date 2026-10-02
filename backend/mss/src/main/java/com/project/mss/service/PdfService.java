package com.project.mss.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.lowagie.text.Chunk;
import com.lowagie.text.ExceptionConverter;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import com.project.mss.exception.BusinessRuleException;

/**
 * PDF reports in the "Classic" layout: logo on top, title, document details,
 * item table and, when applicable, signature fields.
 * Document labels stay in Portuguese because the PDFs are handed to hospitals and suppliers.
 */
@Service
public class PdfService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Color HEADER_GRAY = new Color(0xE6, 0xE6, 0xE6);
    private static final Color ROW_GRAY = new Color(0xF7, 0xF7, 0xF7);

    private final String logoPath;
    private final String companyName;

    public PdfService(@Value("${app.report.logo-path:}") String logoPath,
                      @Value("${app.report.company-name:Baumer}") String companyName) {
        this.logoPath = logoPath;
        this.companyName = companyName;
    }

    /** Content of a tabular report. */
    public record Report(
            String title,
            List<String[]> headerInfo,   // {label, value} pairs shown above the table
            String[] columns,
            float[] widths,
            List<String[]> rows,
            String tableFooter,            // e.g. "Total de itens: 12"
            String[] signatures            // signature labels; empty to hide
    ) { }

    /** Content of the delivery report (Classic layout). */
    public record DeliveryReport(
            String number,
            String hospital,
            String date,
            String deliveredBy,
            List<String[]> rows,   // REF, material, lot, expiry date, quantity
            int totalPieces,
            String notes           // optional; printed below the table when present
    ) { }

    private static final Color TEXT_MUTED = new Color(0x6B, 0x6B, 0x6B);
    private static final Color BORDER = new Color(0xBF, 0xBF, 0xBF);

    /**
     * Delivery report, Classic layout: logo, title and number; boxes with hospital, date,
     * delivered by and total pieces; item table with a total row; signatures; and a footer
     * with the issue date and "page X of Y" on every page.
     */
    public byte[] deliveryReport(DeliveryReport r) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 30, 50);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new IssueFooter(LocalDateTime.now().format(DATE_TIME)));
            doc.open();

            Font fTitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15);
            Font fNumber = FontFactory.getFont(FontFactory.HELVETICA, 9, TEXT_MUTED);
            Font fBoxLabel = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, TEXT_MUTED);
            Font fBoxValue = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font fHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
            Font fCell = FontFactory.getFont(FontFactory.HELVETICA, 8);
            Font fTotal = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
            Font fText = FontFactory.getFont(FontFactory.HELVETICA, 9);

            // Header: logo on the left, title and number on the right
            PdfPTable header = new PdfPTable(new float[]{1.6f, 3f});
            header.setWidthPercentage(100);
            header.addCell(logoCell());
            PdfPCell titleCell = new PdfPCell();
            titleCell.setBorder(Rectangle.NO_BORDER);
            titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            Paragraph title = new Paragraph("ENTREGA DE MATERIAIS", fTitle);
            title.setAlignment(Element.ALIGN_RIGHT);
            Paragraph number = new Paragraph("Entrega nº " + r.number(), fNumber);
            number.setAlignment(Element.ALIGN_RIGHT);
            titleCell.addElement(title);
            titleCell.addElement(number);
            header.addCell(titleCell);
            header.setSpacingAfter(12);
            doc.add(header);

            // Information boxes
            PdfPTable boxes = new PdfPTable(new float[]{2.6f, 1.3f, 1.6f, 1.1f});
            boxes.setWidthPercentage(100);
            boxes.addCell(infoBox("HOSPITAL", r.hospital(), fBoxLabel, fBoxValue));
            boxes.addCell(infoBox("DATA DA ENTREGA", r.date(), fBoxLabel, fBoxValue));
            boxes.addCell(infoBox("ENTREGUE POR", r.deliveredBy(), fBoxLabel, fBoxValue));
            boxes.addCell(infoBox("TOTAL DE PEÇAS", String.valueOf(r.totalPieces()), fBoxLabel, fBoxValue));
            boxes.setSpacingAfter(12);
            doc.add(boxes);

            // Items
            String[] columns = {"REF", "MATERIAL", "LOTE", "VALIDADE", "QTD."};
            PdfPTable table = new PdfPTable(new float[]{1.6f, 3.4f, 1.3f, 1.2f, 0.6f});
            table.setWidthPercentage(100);
            table.setHeaderRows(1);
            for (int c = 0; c < columns.length; c++) {
                PdfPCell cell = new PdfPCell(new Phrase(columns[c], fHeader));
                cell.setBackgroundColor(HEADER_GRAY);
                cell.setBorderColor(BORDER);
                cell.setPadding(5);
                cell.setHorizontalAlignment(c == columns.length - 1 ? Element.ALIGN_CENTER : Element.ALIGN_LEFT);
                table.addCell(cell);
            }
            int i = 0;
            for (String[] row : r.rows()) {
                for (int c = 0; c < row.length; c++) {
                    PdfPCell cell = new PdfPCell(new Phrase(row[c] == null ? "" : row[c], fCell));
                    cell.setBorderColor(BORDER);
                    cell.setPadding(4);
                    cell.setHorizontalAlignment(c == row.length - 1 ? Element.ALIGN_CENTER : Element.ALIGN_LEFT);
                    if (i % 2 == 1) cell.setBackgroundColor(ROW_GRAY);
                    table.addCell(cell);
                }
                i++;
            }
            PdfPCell totalLabel = new PdfPCell(new Phrase("TOTAL", fTotal));
            totalLabel.setColspan(columns.length - 1);
            totalLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalLabel.setBackgroundColor(HEADER_GRAY);
            totalLabel.setBorderColor(BORDER);
            totalLabel.setPadding(5);
            table.addCell(totalLabel);
            PdfPCell totalValue = new PdfPCell(new Phrase(String.valueOf(r.totalPieces()), fTotal));
            totalValue.setHorizontalAlignment(Element.ALIGN_CENTER);
            totalValue.setBackgroundColor(HEADER_GRAY);
            totalValue.setBorderColor(BORDER);
            totalValue.setPadding(5);
            table.addCell(totalValue);
            doc.add(table);

            if (r.notes() != null && !r.notes().isBlank()) {
                Paragraph notes = new Paragraph("Observações: " + r.notes(), fText);
                notes.setSpacingBefore(8);
                doc.add(notes);
            }

            addSignatureLines(doc, new String[]{"Entregue por", "Recebido por (nome legível e data)"}, fText);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessRuleException("Failed to generate PDF: " + e.getMessage());
        }
    }

    public byte[] generate(Report r) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 30, 36);
            PdfWriter.getInstance(doc, out);
            doc.open();

            addLogo(doc);

            Font fTitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
            Font fLabel = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
            Font fText = FontFactory.getFont(FontFactory.HELVETICA, 9);
            Font fHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
            Font fCell = FontFactory.getFont(FontFactory.HELVETICA, 8);

            Paragraph title = new Paragraph(r.title(), fTitle);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(10);
            doc.add(title);

            if (r.headerInfo() != null && !r.headerInfo().isEmpty()) {
                PdfPTable info = new PdfPTable(new float[]{1.3f, 4f});
                info.setWidthPercentage(100);
                for (String[] pair : r.headerInfo()) {
                    info.addCell(borderlessCell(pair[0], fLabel));
                    info.addCell(borderlessCell(pair.length > 1 && pair[1] != null ? pair[1] : "-", fText));
                }
                info.setSpacingAfter(10);
                doc.add(info);
            }

            PdfPTable table = new PdfPTable(r.widths());
            table.setWidthPercentage(100);
            table.setHeaderRows(1);
            for (String col : r.columns()) {
                PdfPCell c = new PdfPCell(new Phrase(col, fHeader));
                c.setBackgroundColor(HEADER_GRAY);
                c.setHorizontalAlignment(Element.ALIGN_CENTER);
                c.setPadding(4);
                table.addCell(c);
            }
            int i = 0;
            for (String[] row : r.rows()) {
                for (String value : row) {
                    PdfPCell c = new PdfPCell(new Phrase(value == null ? "" : value, fCell));
                    c.setPadding(3);
                    if (i % 2 == 1) c.setBackgroundColor(ROW_GRAY);
                    table.addCell(c);
                }
                i++;
            }
            if (r.rows().isEmpty()) {
                PdfPCell empty = new PdfPCell(new Phrase("Nenhum item", fCell));
                empty.setColspan(r.columns().length);
                empty.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(empty);
            }
            doc.add(table);

            if (r.tableFooter() != null) {
                Paragraph footer = new Paragraph(r.tableFooter(), fLabel);
                footer.setAlignment(Element.ALIGN_RIGHT);
                footer.setSpacingBefore(4);
                doc.add(footer);
            }

            if (r.signatures() != null && r.signatures().length > 0) {
                addSignatures(doc, r.signatures(), fText);
            }

            Paragraph issue = new Paragraph("Emitido em " + LocalDateTime.now().format(DATE_TIME),
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 7));
            issue.setAlignment(Element.ALIGN_RIGHT);
            issue.setSpacingBefore(16);
            doc.add(issue);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessRuleException("Failed to generate PDF: " + e.getMessage());
        }
    }

    private void addLogo(Document doc) throws Exception {
        Path logo = logoPath == null || logoPath.isBlank() ? null : Paths.get(logoPath);
        if (logo != null && Files.exists(logo)) {
            Image img = Image.getInstance(Files.readAllBytes(logo));
            img.scaleToFit(150, 55);
            img.setAlignment(Image.ALIGN_CENTER);
            doc.add(img);
        } else {
            // No logo file configured: use the company name as header
            Paragraph p = new Paragraph(companyName.toUpperCase(),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18));
            p.setAlignment(Element.ALIGN_CENTER);
            doc.add(p);
        }
        doc.add(new Paragraph(new Chunk(" ")));
    }

    /** Logo (or company name) aligned to the left, used by the delivery report header. */
    private PdfPCell logoCell() throws Exception {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        Path logo = logoPath == null || logoPath.isBlank() ? null : Paths.get(logoPath);
        if (logo != null && Files.exists(logo)) {
            Image img = Image.getInstance(Files.readAllBytes(logo));
            img.scaleToFit(140, 50);
            cell.addElement(img);
        } else {
            cell.addElement(new Paragraph(companyName.toUpperCase(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16)));
        }
        return cell;
    }

    private PdfPCell infoBox(String label, String value, Font fLabel, Font fValue) {
        PdfPCell cell = new PdfPCell();
        cell.setBorderColor(BORDER);
        cell.setPadding(6);
        cell.addElement(new Paragraph(label, fLabel));
        cell.addElement(new Paragraph(value == null || value.isBlank() ? "-" : value, fValue));
        return cell;
    }

    /** Signature lines side by side, label below each line (no separate date line). */
    private void addSignatureLines(Document doc, String[] labels, Font font) throws Exception {
        PdfPTable signatures = new PdfPTable(labels.length);
        signatures.setWidthPercentage(100);
        signatures.setSpacingBefore(45);
        signatures.setKeepTogether(true);
        for (String label : labels) {
            PdfPCell c = new PdfPCell();
            c.setBorder(Rectangle.NO_BORDER);
            c.setPaddingLeft(14);
            c.setPaddingRight(14);
            Paragraph line = new Paragraph("________________________________________", font);
            line.setAlignment(Element.ALIGN_CENTER);
            Paragraph name = new Paragraph(label, font);
            name.setAlignment(Element.ALIGN_CENTER);
            c.addElement(line);
            c.addElement(name);
            signatures.addCell(c);
        }
        doc.add(signatures);
    }

    /** Footer on every page: issue date on the left, "Página X de Y" on the right. */
    private static class IssueFooter extends PdfPageEventHelper {

        private final String issuedAt;
        private final Font font = FontFactory.getFont(FontFactory.HELVETICA, 7, TEXT_MUTED);
        private PdfTemplate totalPages;
        private BaseFont baseFont;

        IssueFooter(String issuedAt) {
            this.issuedAt = issuedAt;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            totalPages = writer.getDirectContent().createTemplate(30, 10);
            try {
                baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            } catch (Exception e) {
                throw new ExceptionConverter(e);
            }
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            float y = document.bottom() - 20;
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                    new Phrase("Emitido pelo sistema em " + issuedAt, font), document.left(), y, 0);

            String text = "Página " + writer.getPageNumber() + " de ";
            float textWidth = baseFont.getWidthPoint(text, 7);
            float x = document.right() - textWidth - 12;
            cb.beginText();
            cb.setFontAndSize(baseFont, 7);
            cb.setColorFill(TEXT_MUTED);
            cb.setTextMatrix(x, y);
            cb.showText(text);
            cb.endText();
            cb.addTemplate(totalPages, x + textWidth, y);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            totalPages.beginText();
            totalPages.setFontAndSize(baseFont, 7);
            totalPages.setColorFill(TEXT_MUTED);
            totalPages.setTextMatrix(0, 0);
            totalPages.showText(String.valueOf(writer.getPageNumber() - 1));
            totalPages.endText();
        }
    }

    private void addSignatures(Document doc, String[] labels, Font font) throws Exception {
        PdfPTable signatures = new PdfPTable(labels.length);
        signatures.setWidthPercentage(100);
        signatures.setSpacingBefore(40);
        for (String label : labels) {
            PdfPCell c = new PdfPCell();
            c.setBorder(Rectangle.NO_BORDER);
            c.setPaddingLeft(12);
            c.setPaddingRight(12);
            Paragraph signatureLine = new Paragraph("______________________________", font);
            signatureLine.setAlignment(Element.ALIGN_CENTER);
            Paragraph name = new Paragraph(label, font);
            name.setAlignment(Element.ALIGN_CENTER);
            Paragraph dateLine = new Paragraph("Data: ____/____/______", font);
            dateLine.setAlignment(Element.ALIGN_CENTER);
            dateLine.setSpacingBefore(6);
            c.addElement(signatureLine);
            c.addElement(name);
            c.addElement(dateLine);
            signatures.addCell(c);
        }
        doc.add(signatures);
    }

    private PdfPCell borderlessCell(String text, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setBorder(Rectangle.NO_BORDER);
        c.setPadding(2);
        return c;
    }
}
