package com.project.mss.dto.hospital;

import java.util.Set;
import java.util.stream.Collectors;

import com.project.mss.model.entity.Hospital;
import com.project.mss.model.enums.HospitalType;
import com.project.mss.model.enums.ProductLine;
import com.project.mss.model.enums.PriceTableType;

public record HospitalDTO(Long id, String name, String acronym, HospitalType type, PriceTableType priceTableType,
                          Boolean active, Set<ProductLine> productLines, Set<Long> coveredHospitalIds,
                          Long distributionCenterId) {
    public static HospitalDTO of(Hospital h) {
        return new HospitalDTO(h.getId(), h.getName(), h.getAcronym(), h.getType(), h.getPriceTableType(), h.getActive(),
                Set.copyOf(h.getProductLines()),
                h.getCoveredHospitals().stream().map(Hospital::getId).collect(Collectors.toSet()),
                h.getDistributionCenters().stream().map(Hospital::getId).findFirst().orElse(null));    }
}
