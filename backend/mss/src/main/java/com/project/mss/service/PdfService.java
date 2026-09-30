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
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
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
