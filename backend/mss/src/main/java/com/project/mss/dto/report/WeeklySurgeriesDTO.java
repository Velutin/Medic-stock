package com.project.mss.dto.report;

import java.time.LocalDate;
import java.util.List;

/**
 * Weekly closing from Saturday to Friday (paid on Friday).
 * byHospital and bySurgicalTech hold the surgery count for the week.
 */
public record WeeklySurgeriesDTO(
        LocalDate start,
        LocalDate end,
        LocalDate paymentDate,
        int total,
        List<Count> byHospital,
        List<Count> bySurgicalTech
) {
    public record Count(String name, long quantity) { }
}
