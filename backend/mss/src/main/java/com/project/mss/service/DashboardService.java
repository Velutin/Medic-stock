package com.project.mss.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.mss.dto.dashboard.DashboardDTO;
import com.project.mss.dto.replenishment.ReplenishmentSuggestionDTO;
import com.project.mss.dto.stock.StockMovementDTO;
import com.project.mss.model.entity.Hospital;
import com.project.mss.model.enums.Location;
import com.project.mss.model.enums.PendingIssueStatus;
import com.project.mss.model.enums.ShipmentStatus;
import com.project.mss.repository.DeliveryRepository;
import com.project.mss.repository.HospitalRepository;
import com.project.mss.repository.LoanRepository;
import com.project.mss.repository.PendingIssueRepository;
import com.project.mss.repository.StockMovementRepository;
import com.project.mss.repository.StockRepository;
import com.project.mss.repository.SurgeryRepository;

/**
 * Administrator's dashboard: stock indicators, the closing week, open pending issues, invoices that have not
 * arrived, replenishment alerts and latest movements.
 */
@Service
public class DashboardService {

    private static final int EXPIRY_WARNING_DAYS = 30;
    private static final int RECENT_MOVEMENTS = 10;

    private final StockRepository stockRepository;
    private final SurgeryRepository surgeryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final HospitalRepository hospitalRepository;
    private final PendingIssueRepository pendingIssueRepository;
    private final ReplenishmentService replenishmentService;
    private final AccessControlService accessControlService;
    private final SupplierInvoiceService supplierInvoiceService;
    private final DeliveryRepository deliveryRepository;
    private final LoanRepository loanRepository;

    public DashboardService(StockRepository stockRepository, SurgeryRepository surgeryRepository,
                            StockMovementRepository stockMovementRepository, HospitalRepository hospitalRepository,
                            PendingIssueRepository pendingIssueRepository, ReplenishmentService replenishmentService,
                            AccessControlService accessControlService, SupplierInvoiceService supplierInvoiceService,
                            DeliveryRepository deliveryRepository, LoanRepository loanRepository) {
        this.stockRepository = stockRepository;
        this.surgeryRepository = surgeryRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.hospitalRepository = hospitalRepository;
        this.pendingIssueRepository = pendingIssueRepository;
        this.replenishmentService = replenishmentService;
        this.accessControlService = accessControlService;
        this.supplierInvoiceService = supplierInvoiceService;
        this.deliveryRepository = deliveryRepository;
        this.loanRepository = loanRepository;
    }

    /** hospitalId empty: every hospital (and distribution center). */
    @Transactional(readOnly = true)
    public DashboardDTO dashboard(Long hospitalId) {
        accessControlService.requireManager();
        Set<Long> hospitals = hospitalId != null
                ? Set.of(accessControlService.requireHospitalAccess(hospitalId).getId())
                : accessControlService.allowedHospitals();
        LocalDate today = LocalDate.now();
        LocalDate weekStart = ReportService.weekStart(today);
        LocalDate weekEnd = weekStart.plusDays(6);

        // Material on its way has no hospital decided yet, so the count is the same for every selection
        long overdueInvoices = supplierInvoiceService.countOverdue();

        if (hospitals.isEmpty()) {
            return new DashboardDTO(weekStart, weekEnd, 0, 0, 0, 0, 0, overdueInvoices, null, null,
                    List.of(), List.of(), List.of(), 0, 0, null);
        }

        long units = stockRepository.sumValidUnits(hospitals, today);
        long inTransitUnits = stockRepository.sumValidUnitsAt(hospitals, Location.IN_TRANSIT, today);
        long expiring = stockRepository.countLotsExpiringBetween(hospitals, today, today.plusDays(EXPIRY_WARNING_DAYS));
        long expired = stockRepository.countExpiredLots(hospitals, today);

        List<DashboardDTO.HospitalSurgeries> byHospital = surgeryRepository.countByHospital(hospitals, weekStart, weekEnd)
                .stream()
                .map(r -> new DashboardDTO.HospitalSurgeries((Long) r[0], (String) r[1], (Long) r[2]))
                .toList();
        long weekSurgeries = byHospital.stream().mapToLong(DashboardDTO.HospitalSurgeries::surgeries).sum();

        long pending = pendingIssueRepository.countByHospitalIdInAndStatus(hospitals, PendingIssueStatus.OPEN);
        LocalDate oldestPending = pending > 0 ? pendingIssueRepository.oldestSurgeryDate(hospitals, PendingIssueStatus.OPEN) : null;
        LocalDate latestPending = pending > 0 ? pendingIssueRepository.latestSurgeryDate(hospitals, PendingIssueStatus.OPEN) : null;

        List<DashboardDTO.ReplenishmentAlert> alerts = new ArrayList<>();
        for (Hospital h : hospitalRepository.findAllById(hospitals)) {
            if (!Boolean.TRUE.equals(h.getActive())) continue;
            for (ReplenishmentSuggestionDTO s : replenishmentService.suggestion(h.getId(), true)) {
                alerts.add(new DashboardDTO.ReplenishmentAlert(h.getId(), h.getName(), s.materialId(), s.ref(),
                        s.description(), s.replenishFromStoreroom(), s.orderFromSupplier()));
            }
        }
        alerts.sort(Comparator.comparing(DashboardDTO.ReplenishmentAlert::hospital)
                .thenComparing(DashboardDTO.ReplenishmentAlert::ref));

        // Shipments still on the road towards one of these hospitals. Counted here and not through the
        // listing of the arrivals screen because that one is bound to what the user may confirm, while the
        // card follows the hospital chosen on this screen.
        List<java.time.LocalDateTime> dispatches = new ArrayList<>();
        deliveryRepository.findByStatusOrderByCreatedAtDesc(ShipmentStatus.DISPATCHED).stream()
                .filter(d -> hospitals.contains(d.getHospital().getId()))
                .forEach(d -> dispatches.add(d.getCreatedAt()));
        loanRepository.findByStatusOrderByCreatedAtDesc(ShipmentStatus.DISPATCHED).stream()
                .filter(e -> e.getDestinationHospital() != null
                        && hospitals.contains(e.getDestinationHospital().getId()))
                .forEach(e -> dispatches.add(e.getCreatedAt()));
        LocalDate oldestDispatch = dispatches.stream().min(java.time.LocalDateTime::compareTo)
                .map(java.time.LocalDateTime::toLocalDate).orElse(null);

        List<StockMovementDTO> movements = stockMovementRepository
                .latest(hospitals, PageRequest.of(0, RECENT_MOVEMENTS)).stream()
                .map(StockMovementDTO::of).toList();

        return new DashboardDTO(weekStart, weekEnd, units, expiring, expired, weekSurgeries,
                pending, overdueInvoices, oldestPending, latestPending, byHospital, alerts, movements,
                inTransitUnits, dispatches.size(), oldestDispatch);
    }
}
