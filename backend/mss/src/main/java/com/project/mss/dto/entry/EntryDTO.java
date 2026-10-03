package com.project.mss.dto.entry;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.project.mss.model.entity.Lot;
import com.project.mss.model.entity.Material;
import com.project.mss.model.entity.StockEntry;
import com.project.mss.model.entity.User;

/** Stock entry with its items (lots), totals and who registered or last corrected it. */
public record EntryDTO(Long id, Long hospitalId, String hospital, LocalDate entryDate, String notes,
                       String createdBy, LocalDateTime createdAt, String updatedBy, LocalDateTime updatedAt,
                       int lots, int units, List<EntryLine> items) {

    public record EntryLine(Long lotId, Long materialId, String ref, String description, String component,
                            String size, String color, String lot, LocalDate expiryDate, int quantity) { }

    public static EntryDTO of(StockEntry e) {
        List<EntryLine> lines = e.getItems().stream().map(i -> {
            Lot l = i.getLot();
            Material m = l.getMaterial();
            return new EntryLine(l.getId(), m.getId(), m.getRef(), m.getDescription(), m.getComponent(), m.getSize(),
                    m.getColor(), l.getNumber(), l.getExpiryDate(), i.getQuantity());
        }).toList();
        return new EntryDTO(e.getId(), e.getHospital().getId(), e.getHospital().getName(), e.getEntryDate(), e.getNotes(),
                name(e.getCreatedBy()), e.getCreatedAt(), name(e.getUpdatedBy()), e.getUpdatedAt(),
                lines.size(), lines.stream().mapToInt(EntryLine::quantity).sum(), lines);
    }

    private static String name(User u) {
        return u == null ? null : u.getName();
    }
}
