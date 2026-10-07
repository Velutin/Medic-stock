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
    /** System colors used by the movement documents. */
    private static final Color BRAND_DARK = new Color(0x12, 0x30, 0x2C);
    private static final Color BRAND = new Color(0x0F, 0x5C, 0x55);
    private static final Color BRAND_LIGHT = new Color(0xEE, 0xF6, 0xF3);
    private static final Color RULE = new Color(0xC9, 0xD6, 0xD2);
    private static final Color KPI_BG = new Color(0xE3, 0xF1, 0xEC);

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

    /**
     * Any material movement document (delivery, loan delivered as a delivery, return): title and number,
     * information pairs (label, value), the item table with a total, optional notes and the signature lines.
     */
    public record ClassicReport(
            String title,             // e.g. "ENTREGA DE MATERIAIS"
            String number,            // e.g. "Entrega nº 123"
            List<String[]> boxes,     // {LABEL, value}, shown in two columns
            float[] boxWidths,        // kept for compatibility; the layout uses two columns
            List<String[]> rows,      // REF, material, lot, expiry date, quantity
            int totalPieces,
            String notesLabel,        // e.g. "Observações" or "Motivo"
            String notes,             // optional
            String[] signatures
    ) { }

    private static final Color TEXT_MUTED = new Color(0x6B, 0x6B, 0x6B);

    /**
     * Delivery report, Classic layout: logo, title and number; boxes with hospital, date,
     * delivered by and total pieces; item table with a total row; signatures; and a footer
     * with the issue date and "page X of Y" on every page.
     */
    public byte[] deliveryReport(DeliveryReport r) {
        return classicReport(new ClassicReport("ENTREGA DE MATERIAIS", "Entrega nº " + r.number(),
                List.of(new String[]{"HOSPITAL", r.hospital()}, new String[]{"DATA DA ENTREGA", r.date()},
                        new String[]{"ENTREGUE POR", r.deliveredBy()},
                        new String[]{"TOTAL DE PEÇAS", String.valueOf(r.totalPieces())}),
                new float[]{2.6f, 1.3f, 1.6f, 1.1f}, r.rows(), r.totalPieces(), "Observações", r.notes(),
                new String[]{"Entregue por", "Recebido por (nome legível e data)"}));
    }

    /**
     * Layout shared by every material movement document (delivery, loan delivered as a delivery, return):
     * logo with title and number on the right over a green rule; information in two columns (label above value);
     * item table with a dark green header, alternating light green rows and the total; optional notes; signature
     * lines at the bottom of the last page; and the footer with the issue date and "Página X de Y".
     */
    public byte[] classicReport(ClassicReport r) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 30, 50);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new IssueFooter(LocalDateTime.now().format(DATE_TIME)));
            doc.open();

            Font fTitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 19, BRAND_DARK);
            Font fNumber = FontFactory.getFont(FontFactory.HELVETICA, 9, TEXT_MUTED);
            Font fLabel = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, TEXT_MUTED);
            Font fValue = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
            Font fHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, Color.WHITE);
            Font fCell = FontFactory.getFont(FontFactory.HELVETICA, 8.5f);
            Font fRef = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f);
            Font fTotal = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font fTotalValue = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, BRAND);
            Font fText = FontFactory.getFont(FontFactory.HELVETICA, 9);
            Font fTextBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);

            // Header: logo on the left, title and number on the right, green rule below
            PdfPTable header = new PdfPTable(new float[]{1.6f, 2.4f});
            header.setWidthPercentage(100);
            PdfPCell logo = logoCell();
            underline(logo);
            header.addCell(logo);
            PdfPCell titleCell = new PdfPCell();
            underline(titleCell);
            titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            Paragraph title = new Paragraph(r.title(), fTitle);
            title.setAlignment(Element.ALIGN_RIGHT);
            Paragraph number = new Paragraph(r.number(), fNumber);
            number.setAlignment(Element.ALIGN_RIGHT);
            titleCell.addElement(title);
            titleCell.addElement(number);
            header.addCell(titleCell);
            header.setSpacingAfter(12);
            doc.add(header);

            // Information in two columns: small label above the value
            PdfPTable info = new PdfPTable(2);
            info.setWidthPercentage(100);
            for (String[] box : r.boxes()) {
                PdfPCell c = new PdfPCell();
                c.setBorder(Rectangle.NO_BORDER);
                c.setPaddingLeft(0);
                c.setPaddingBottom(8);
                c.addElement(new Paragraph(box[0], fLabel));
                c.addElement(new Paragraph(box[1] == null || box[1].isBlank() ? "-" : box[1], fValue));
                info.addCell(c);
            }
            if (r.boxes().size() % 2 == 1) {
                PdfPCell empty = new PdfPCell();
                empty.setBorder(Rectangle.NO_BORDER);
                info.addCell(empty);
            }
            info.setSpacingAfter(6);
            doc.add(info);

            // Items: dark green header, alternating light green rows, total below a heavier line
            String[] columns = {"REF", "MATERIAL", "LOTE", "VALIDADE", "QTD."};
            PdfPTable table = new PdfPTable(new float[]{1.6f, 2.8f, 1.3f, 1.4f, 1f});
            table.setWidthPercentage(100);
            table.setHeaderRows(1);
            for (int c = 0; c < columns.length; c++) {
                PdfPCell cell = new PdfPCell(new Phrase(columns[c], fHeader));
                cell.setBackgroundColor(BRAND_DARK);
                cell.setBorder(Rectangle.NO_BORDER);
                cell.setPadding(6);
                cell.setHorizontalAlignment(c == columns.length - 1 ? Element.ALIGN_CENTER : Element.ALIGN_LEFT);
                table.addCell(cell);
            }
            int i = 0;
            for (String[] row : r.rows()) {
                for (int c = 0; c < row.length; c++) {
                    PdfPCell cell = new PdfPCell(new Phrase(row[c] == null ? "" : row[c], c == 0 ? fRef : fCell));
                    cell.setBorder(Rectangle.BOTTOM);
                    cell.setBorderColorBottom(RULE);
                    cell.setBorderWidthBottom(0.3f);
                    cell.setPadding(5);
                    cell.setHorizontalAlignment(c == row.length - 1 ? Element.ALIGN_CENTER : Element.ALIGN_LEFT);
                    if (i % 2 == 0) cell.setBackgroundColor(BRAND_LIGHT);
                    table.addCell(cell);
                }
                i++;
            }
            PdfPCell totalLabel = new PdfPCell(new Phrase("TOTAL", fTotal));
            totalLabel.setColspan(columns.length - 1);
            totalLabel.setBorder(Rectangle.TOP);
            totalLabel.setBorderColorTop(BRAND_DARK);
            totalLabel.setBorderWidthTop(1.4f);
            totalLabel.setPaddingTop(7);
            totalLabel.setPaddingBottom(5);
            table.addCell(totalLabel);
            PdfPCell totalValue = new PdfPCell(new Phrase(String.valueOf(r.totalPieces()), fTotalValue));
            totalValue.setHorizontalAlignment(Element.ALIGN_CENTER);
            totalValue.setBorder(Rectangle.TOP);
            totalValue.setBorderColorTop(BRAND_DARK);
            totalValue.setBorderWidthTop(1.4f);
            totalValue.setPaddingTop(7);
            totalValue.setPaddingBottom(5);
            table.addCell(totalValue);
            doc.add(table);

            if (r.notes() != null && !r.notes().isBlank()) {
                Paragraph notes = new Paragraph();
                notes.add(new Chunk(r.notesLabel() + ": ", fTextBold));
                notes.add(new Chunk(r.notes(), fText));
                notes.setSpacingBefore(10);
                doc.add(notes);
            }

            // Signature lines at the bottom of the last page (a new page when the table reaches them)
            float signatureY = doc.bottom() + 70;
            if (writer.getVerticalPosition(true) - 30 < signatureY + 20) {
                doc.newPage();
                writer.setPageEmpty(false);
            }
            PdfContentByte cb = writer.getDirectContent();
            String[] labels = r.signatures();
            float gap = 28;
            float width = (doc.right() - doc.left() - gap * (labels.length - 1)) / labels.length;
            Font fSignature = FontFactory.getFont(FontFactory.HELVETICA, 8);
            for (int s = 0; s < labels.length; s++) {
                float x = doc.left() + s * (width + gap);
                cb.setColorStroke(BRAND_DARK);
                cb.setLineWidth(0.7f);
                cb.moveTo(x, signatureY);
                cb.lineTo(x + width, signatureY);
                cb.stroke();
                ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, new Phrase(labels[s], fSignature),
                        x + width / 2, signatureY - 12, 0);
            }

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessRuleException("Failed to generate PDF: " + e.getMessage());
        }
    }

    /** Report of the Reports and Billing screens: dark green band, highlighted numbers and sections. */
    public record SummaryReport(
            String title,               // e.g. "CONSUMO POR CIRURGIA"
            String subtitle,            // shown on the right of the band, e.g. period and hospital
            List<String[]> highlights,  // {value, label}, the big numbers below the band
            List<Section> sections,
            String note                 // optional text at the end
    ) { }

    /**
     * Section of a summary report: optional heading (with a value on the right and a line below it) and a table.
     * rightAligned: indexes of the numeric columns. total: optional last row in bold above a green line.
     */
    public record Section(
            String heading,
            String headingValue,
            String headingNote,
            String[] columns,
            float[] widths,
            int[] rightAligned,
            List<String[]> rows,
            String[] total
    ) { }

    public byte[] summaryReport(SummaryReport r) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 30, 50);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new IssueFooter(LocalDateTime.now().format(DATE_TIME)));
            doc.open();

            Font fBandTitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Color.WHITE);
            Font fBandSub = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, new Color(0xCF, 0xE3, 0xDE));
            Font fBig = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, BRAND);
            Font fBigLabel = FontFactory.getFont(FontFactory.HELVETICA, 8, TEXT_MUTED);
            Font fHeading = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font fHeadingNote = FontFactory.getFont(FontFactory.HELVETICA, 8, TEXT_MUTED);
            Font fColumn = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, TEXT_MUTED);
            Font fCell = FontFactory.getFont(FontFactory.HELVETICA, 8);
            Font fCellBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
            Font fNote = FontFactory.getFont(FontFactory.HELVETICA, 8, TEXT_MUTED);

            // Logo, then the band with the title and the subtitle
            PdfPTable logoRow = new PdfPTable(new float[]{1.6f, 2.4f});
            logoRow.setWidthPercentage(100);
            logoRow.addCell(logoCell());
            PdfPCell blank = new PdfPCell();
            blank.setBorder(Rectangle.NO_BORDER);
            logoRow.addCell(blank);
            logoRow.setSpacingAfter(6);
            doc.add(logoRow);

            PdfPTable band = new PdfPTable(new float[]{1f, 1f});
            band.setWidthPercentage(100);
            PdfPCell bandTitle = new PdfPCell(new Phrase(r.title(), fBandTitle));
            PdfPCell bandSub = new PdfPCell(new Phrase(r.subtitle() == null ? "" : r.subtitle(), fBandSub));
            bandSub.setHorizontalAlignment(Element.ALIGN_RIGHT);
            for (PdfPCell c : new PdfPCell[]{bandTitle, bandSub}) {
                c.setBackgroundColor(BRAND_DARK);
                c.setBorder(Rectangle.NO_BORDER);
                c.setVerticalAlignment(Element.ALIGN_MIDDLE);
                c.setFixedHeight(42);
                c.setPaddingLeft(12);
                c.setPaddingRight(12);
                band.addCell(c);
            }
            band.setSpacingAfter(10);
            doc.add(band);

            // Highlighted numbers
            if (r.highlights() != null && !r.highlights().isEmpty()) {
                PdfPTable big = new PdfPTable(r.highlights().size());
                big.setWidthPercentage(100);
                for (String[] h : r.highlights()) {
                    PdfPCell c = new PdfPCell();
                    c.setBackgroundColor(KPI_BG);
                    c.setBorder(Rectangle.RIGHT);
                    c.setBorderColorRight(Color.WHITE);
                    c.setBorderWidthRight(1.5f);
                    c.setPadding(9);
                    c.addElement(new Paragraph(h[0], fBig));
                    c.addElement(new Paragraph(h[1], fBigLabel));
                    big.addCell(c);
                }
                big.setSpacingAfter(12);
                doc.add(big);
            }

            for (Section section : r.sections()) {
                java.util.Set<Integer> right = new java.util.HashSet<>();
                if (section.rightAligned() != null) for (int c : section.rightAligned()) right.add(c);

                PdfPTable head = null;
                if (section.heading() != null) {
                    head = new PdfPTable(new float[]{2.4f, 1f});
                    head.setWidthPercentage(100);
                    PdfPCell name = new PdfPCell();
                    name.setBorder(Rectangle.BOTTOM);
                    name.setBorderColorBottom(BRAND);
                    name.setBorderWidthBottom(1.2f);
                    name.setPaddingLeft(0);
                    name.setPaddingBottom(5);
                    name.addElement(new Paragraph(section.heading(), fHeading));
                    if (section.headingNote() != null) name.addElement(new Paragraph(section.headingNote(), fHeadingNote));
                    head.addCell(name);
                    PdfPCell value = new PdfPCell(new Phrase(section.headingValue() == null ? "" : section.headingValue(), fHeading));
                    value.setBorder(Rectangle.BOTTOM);
                    value.setBorderColorBottom(BRAND);
                    value.setBorderWidthBottom(1.2f);
                    value.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    value.setVerticalAlignment(Element.ALIGN_TOP);
                    value.setPaddingRight(0);
                    head.addCell(value);
                }

                PdfPTable table = new PdfPTable(section.widths());
                table.setWidthPercentage(100);
                table.setHeaderRows(1);
                for (int c = 0; c < section.columns().length; c++) {
                    PdfPCell cell = new PdfPCell(new Phrase(section.columns()[c], fColumn));
                    cell.setBorder(Rectangle.BOTTOM);
                    cell.setBorderColorBottom(TEXT_MUTED);
                    cell.setBorderWidthBottom(0.8f);
                    cell.setPadding(4);
                    cell.setHorizontalAlignment(right.contains(c) ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
                    table.addCell(cell);
                }
                for (String[] row : section.rows()) {
                    for (int c = 0; c < row.length; c++) {
                        PdfPCell cell = new PdfPCell(new Phrase(row[c] == null ? "" : row[c], fCell));
                        cell.setBorder(Rectangle.BOTTOM);
                        cell.setBorderColorBottom(RULE);
                        cell.setBorderWidthBottom(0.3f);
                        cell.setPadding(4);
                        cell.setHorizontalAlignment(right.contains(c) ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
                        table.addCell(cell);
                    }
                }
                if (section.total() != null) {
                    for (int c = 0; c < section.total().length; c++) {
                        PdfPCell cell = new PdfPCell(new Phrase(section.total()[c] == null ? "" : section.total()[c], fCellBold));
                        cell.setBorder(Rectangle.TOP);
                        cell.setBorderColorTop(BRAND);
                        cell.setBorderWidthTop(1.2f);
                        cell.setPadding(5);
                        cell.setHorizontalAlignment(right.contains(c) ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
                        table.addCell(cell);
                    }
                }
                if (section.rows().isEmpty()) {
                    PdfPCell empty = new PdfPCell(new Phrase("Nenhum registro.", fNote));
                    empty.setColspan(section.columns().length);
                    empty.setBorder(Rectangle.NO_BORDER);
                    empty.setPadding(6);
                    table.addCell(empty);
                }
                if (section.rows().size() <= 15) {
                    // short sections stay on one page together with their heading
                    PdfPTable block = new PdfPTable(1);
                    block.setWidthPercentage(100);
                    block.setKeepTogether(true);
                    block.setSpacingAfter(14);
                    if (head != null) {
                        PdfPCell headCell = new PdfPCell(head);
                        headCell.setBorder(Rectangle.NO_BORDER);
                        headCell.setPadding(0);
                        headCell.setPaddingBottom(4);
                        block.addCell(headCell);
                    }
                    PdfPCell tableCell = new PdfPCell(table);
                    tableCell.setBorder(Rectangle.NO_BORDER);
                    tableCell.setPadding(0);
                    block.addCell(tableCell);
                    doc.add(block);
                } else {
                    // long tables flow across pages; the column header repeats on each page
                    if (head != null) {
                        head.setSpacingAfter(4);
                        doc.add(head);
                    }
                    table.setSpacingAfter(14);
                    doc.add(table);
                }
            }

            if (r.note() != null && !r.note().isBlank()) doc.add(new Paragraph(r.note(), fNote));
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessRuleException("Failed to generate PDF: " + e.getMessage());
        }
    }

    /** Green rule below the header cells. */
    private static void underline(PdfPCell cell) {
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColorBottom(BRAND);
        cell.setBorderWidthBottom(2.2f);
        cell.setPaddingBottom(10);
        cell.setPaddingLeft(0);
        cell.setPaddingRight(0);
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
