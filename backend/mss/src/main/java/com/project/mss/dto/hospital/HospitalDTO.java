package com.project.mss.dto.hospital;

import java.util.Set;

import com.project.mss.model.entity.Hospital;
import com.project.mss.model.enums.ProductLine;
import com.project.mss.model.enums.PriceTableType;

public record HospitalDTO(Long id, String name, String acronym, PriceTableType priceTableType, Boolean active, Set<ProductLine> productLines) {
    public static HospitalDTO of(Hospital h) {
        return new HospitalDTO(h.getId(), h.getName(), h.getAcronym(), h.getPriceTableType(), h.getActive(), Set.copyOf(h.getProductLines()));
    }
}
