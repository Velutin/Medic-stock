package com.project.mss.dto.material;

import com.project.mss.model.entity.Material;

public record MaterialDTO(Long id, String ref, String description, String gtin, String component,
                          String size, String color, Boolean active) {
    public static MaterialDTO of(Material m) {
        return new MaterialDTO(m.getId(), m.getRef(), m.getDescription(), m.getGtin(), m.getComponent(),
                m.getSize(), m.getColor(), m.getActive());
    }
}
