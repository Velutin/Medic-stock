package com.project.mss.dto.material;

import java.util.List;

import com.project.mss.model.entity.Material;
import com.project.mss.model.enums.ProductLine;

public record MaterialDTO(Long id, String ref, String description, String gtin, List<ProductLine> productLines,
                          String component, String size, String color, Boolean active,
                          Long sectionId, String section) {
    public static MaterialDTO of(Material m) {
        return new MaterialDTO(m.getId(), m.getRef(), m.getDescription(), m.getGtin(),
                m.getProductLines().stream().sorted().toList(), m.getComponent(),
                m.getSize(), m.getColor(), m.getActive(),
                m.getSection() == null ? null : m.getSection().getId(),
                m.getSection() == null ? null : m.getSection().getName());
    }
}
