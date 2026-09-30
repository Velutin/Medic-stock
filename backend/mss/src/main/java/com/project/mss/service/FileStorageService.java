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
 * Stores consumption sheets. Accepts a PDF or one or more photos;
 * photos are merged into a single PDF (one page per photo).
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
