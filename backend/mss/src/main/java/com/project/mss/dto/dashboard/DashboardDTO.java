package com.project.mss.dto.dashboard;

import java.time.LocalDate;
import java.util.List;

import com.project.mss.dto.stock.StockMovementDTO;

/**
 * Administrator's dashboard for one hospital or every hospital.
 *  - stockUnits: units in the hospitals and storeroom, valid lots only (expired lots are not counted);
 *  - lotsExpiringIn30Days / expiredLots: lots with balance, shown separately;
 *  - week*: closing week from Saturday to Friday, surgeries by surgery date, cancelled ones excluded;
 *  - openPendingIssues: surgery withdrawal items still waiting for a resolution (any date); the oldest and latest
 *    surgery dates among them let the Reports screen open the pending issues report with a period that holds all;
 *  - replenishmentAlerts: materials below the hospital ideal or the ideal total.
 */
public record DashboardDTO(
        LocalDate weekStart,
        LocalDate weekEnd,
        long stockUnits,
        long lotsExpiringIn30Days,
        long expiredLots,
        long weekSurgeries,
        long openPendingIssues,
        LocalDate oldestPendingDate,
        LocalDate latestPendingDate,
        List<HospitalSurgeries> surgeriesByHospital,
        List<ReplenishmentAlert> replenishmentAlerts,
        List<StockMovementDTO> recentMovements
) {
    public record HospitalSurgeries(Long hospitalId, String hospital, long surgeries) { }

    public record ReplenishmentAlert(Long hospitalId, String hospital, Long materialId, String ref, String description,
                                     int replenishFromStoreroom, int orderFromSupplier) { }
}
