package com.project.mss.dto.surgery;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

/** Lots extracted from the consumption sheet: each label counts as 1 item. */
public record SheetItemsDTO(@NotEmpty List<LabelDTO> labels) {
    public record LabelDTO(String lot, String ref) { }
}
