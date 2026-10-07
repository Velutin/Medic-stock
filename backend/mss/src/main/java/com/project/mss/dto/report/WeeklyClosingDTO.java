package com.project.mss.dto.report;

import java.time.LocalDate;
import java.util.List;

/**
 * Weekly closing (Saturday to Friday, paid on Friday): open and completed surgeries by surgery date, per hospital.
 * surgicalTechs: surgical techs assigned to the hospital (every surgery of the hospital counts for all of them).
 */
public record WeeklyClosingDTO(LocalDate start, LocalDate end, LocalDate paymentDate, int surgeries, int items,
                               List<HospitalRow> byHospital) {
    public record HospitalRow(Long hospitalId, String hospital, int surgeries, int items, List<String> surgicalTechs) { }
}
