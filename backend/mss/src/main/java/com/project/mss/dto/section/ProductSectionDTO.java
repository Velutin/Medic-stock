package com.project.mss.dto.section;

import com.project.mss.model.entity.ProductSection;

/** Section with the number of catalog items linked to it. */
public record ProductSectionDTO(Long id, String name, Integer displayOrder, Boolean active, long materials) {

    public static ProductSectionDTO of(ProductSection s, long materials) {
        return new ProductSectionDTO(s.getId(), s.getName(), s.getDisplayOrder(), s.getActive(), materials);
    }
}
