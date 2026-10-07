package com.project.mss.model.enums;

import java.text.Normalizer;
import java.util.EnumSet;
import java.util.Set;

public enum ProductLine {
    HIP,
    KNEE,
    SHOULDER;

    /**
     * Parses a product line from spreadsheet text. Accepts the English constant
     * or the Portuguese label used in the spreadsheets (QUADRIL, JOELHO, OMBRO).
     */
    public static ProductLine fromLabel(String label) {
        String n = Normalizer.normalize(label.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase();
        return switch (n) {
            case "HIP", "QUADRIL" -> HIP;
            case "KNEE", "JOELHO" -> KNEE;
            case "SHOULDER", "OMBRO" -> SHOULDER;
            default -> throw new IllegalArgumentException("Unknown product line: " + label);
        };
    }

    /** Parses several lines separated by comma, semicolon or slash (e.g. "QUADRIL, JOELHO, OMBRO"). */
    public static Set<ProductLine> fromLabels(String labels) {
        Set<ProductLine> result = EnumSet.noneOf(ProductLine.class);
        for (String part : labels.split("[,;/]")) {
            if (!part.isBlank()) result.add(fromLabel(part));
        }
        return result;
    }
}
