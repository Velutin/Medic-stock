package com.project.mss.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.lowagie.text.Document;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.PdfWriter;
import com.project.mss.exception.BusinessRuleException;
import com.project.mss.exception.EntityNotFoundException;

/**
 * Stores consumption sheets and the DANFEs that come with the supplier invoices.
 *
 * Sheets accept a PDF or one or more photos (merged into a single PDF, one page per photo). Invoice PDFs
 * arrive by e-mail already as PDFs and are stored as they came; they are the only files here that expire,
 * deleted by age so the folder does not grow forever.
 */
@Service
public class FileStorageService {

    private final Path root;

    public FileStorageService(@Value("${app.storage.dir:./storage}") String directory) {
        this.root = Paths.get(directory).toAbsolutePath().normalize();
    }

    public String saveSheet(Long surgeryId, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BusinessRuleException("Send the sheet as a PDF or as photos");
        }
        try {
            byte[] pdf;
            if (files.size() == 1 && isPdf(files.get(0).getBytes())) {
                pdf = files.get(0).getBytes();
            } else {
                pdf = photosToPdf(files);
            }
            Path folder = root.resolve("fichas").resolve(String.valueOf(surgeryId));
            Files.createDirectories(folder);
            Path destination = folder.resolve("sheet-" + UUID.randomUUID() + ".pdf");
            Files.write(destination, pdf);
            return root.relativize(destination).toString();
        } catch (BusinessRuleException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessRuleException("Could not save the sheet: " + e.getMessage());
        }
    }

    /**
     * Stores the DANFE of an invoice. Refuses anything that is not really a PDF: the name of an attachment
     * is whatever the sender typed, the first bytes are what the file actually is.
     */
    public String saveInvoicePdf(Long invoiceId, byte[] pdf) {
        if (pdf == null || !isPdf(pdf)) {
            throw new BusinessRuleException("The attachment is not a PDF");
        }
        try {
            Path folder = root.resolve("notas").resolve(String.valueOf(invoiceId));
            Files.createDirectories(folder);
            Path destination = folder.resolve("danfe-" + UUID.randomUUID() + ".pdf");
            Files.write(destination, pdf);
            return root.relativize(destination).toString();
        } catch (Exception e) {
            throw new BusinessRuleException("Could not save the invoice PDF: " + e.getMessage());
        }
    }

    /** Deletes a stored file. Answers whether it was there, and never throws: this runs unattended. */
    public boolean delete(String relativePath) {
        if (relativePath == null) return false;
        Path file = root.resolve(relativePath).normalize();
        if (!file.startsWith(root)) return false;
        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            return false;
        }
    }

    public byte[] read(String relativePath) {
        Path file = root.resolve(relativePath).normalize();
        if (!file.startsWith(root) || !Files.exists(file)) {
            throw new EntityNotFoundException("File not found");
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new BusinessRuleException("Could not read the file");
        }
    }

    private byte[] photosToPdf(List<MultipartFile> photos) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 20, 20, 20, 20);
        PdfWriter.getInstance(doc, out);
        doc.open();
        boolean first = true;
        for (MultipartFile photo : photos) {
            byte[] bytes = photo.getBytes();
            if (!isJpeg(bytes) && !isPng(bytes)) {
                doc.close();
                throw new BusinessRuleException("Unsupported format in " + photo.getOriginalFilename()
                        + ". Send PDF, JPG or PNG (on iPhone, set the camera format to 'Most Compatible').");
            }
            Image img = Image.getInstance(bytes);
            float width = PageSize.A4.getWidth() - 40;
            float height = PageSize.A4.getHeight() - 40;
            img.scaleToFit(width, height);
            img.setAlignment(Image.ALIGN_CENTER);
            if (!first) doc.newPage();
            doc.add(img);
            first = false;
        }
        doc.close();
        return out.toByteArray();
    }

    private static boolean isPdf(byte[] b) {
        return b.length > 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F';
    }

    private static boolean isJpeg(byte[] b) {
        return b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8;
    }

    private static boolean isPng(byte[] b) {
        return b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
    }
}
