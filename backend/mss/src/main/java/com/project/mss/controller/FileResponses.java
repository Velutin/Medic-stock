package com.project.mss.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Builds file download responses. */
final class FileResponses {

    private FileResponses() { }

    static ResponseEntity<byte[]> pdf(byte[] content, String name, boolean inline) {
        return file(content, name, MediaType.APPLICATION_PDF, inline);
    }

    static ResponseEntity<byte[]> xlsx(byte[] content, String name) {
        return file(content, name,
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"), false);
    }

    private static ResponseEntity<byte[]> file(byte[] content, String name, MediaType type, boolean inline) {
        ContentDisposition cd = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(name).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .contentType(type)
                .body(content);
    }
}
