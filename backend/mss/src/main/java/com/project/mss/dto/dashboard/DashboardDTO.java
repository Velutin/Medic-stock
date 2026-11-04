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
 *  - replenishmentAlerts: materials below the hospital ideal or the ideal total;
 *  - overdueInvoices: invoices open for longer than the configured number of days. Counted as invoices, not
 *    items, and never filtered by hospital: material coming from the supplier arrives at the storeroom with
 *    no hospital decided yet, so the number is the same whichever hospital is selected.
 *  - inTransitUnits / openShipments / oldestDispatch: material dispatched to the hospitals and not yet
 *    confirmed. The two numbers can disagree, and that is information, not a bug: units that stayed on the
 *    road as a pending issue belong to a shipment already closed, so they are counted in the units and not
 *    in the shipments. oldestDispatch is the dispatch date of the oldest open shipment, which is what turns
 *    a forgotten box into something visible instead of a balance nobody looks for.
 */
public record DashboardDTO(
        LocalDate weekStart,
        LocalDate weekEnd,
        long stockUnits,
        long lotsExpiringIn30Days,
        long expiredLots,
        long weekSurgeries,
        long openPendingIssues,
        long overdueInvoices,
        LocalDate oldestPendingDate,
        LocalDate latestPendingDate,
        List<HospitalSurgeries> surgeriesByHospital,
        List<ReplenishmentAlert> replenishmentAlerts,
        List<StockMovementDTO> recentMovements,
        long inTransitUnits,
        long openShipments,
        LocalDate oldestDispatch
) {
    public record HospitalSurgeries(Long hospitalId, String hospital, long surgeries) { }

    public record ReplenishmentAlert(Long hospitalId, String hospital, Long materialId, String ref, String description,
                                     int replenishFromStoreroom, int orderFromSupplier) { }
}
