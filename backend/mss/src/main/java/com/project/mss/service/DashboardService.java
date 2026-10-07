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
import com.project.mss.model.enums.PendingIssueStatus;
import com.project.mss.repository.HospitalRepository;
import com.project.mss.repository.PendingIssueRepository;
import com.project.mss.repository.StockMovementRepository;
import com.project.mss.repository.StockRepository;
import com.project.mss.repository.SurgeryRepository;

/** Administrator's dashboard: stock indicators, the closing week, open pending issues, replenishment alerts and latest movements. */
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

    public DashboardService(StockRepository stockRepository, SurgeryRepository surgeryRepository,
                            StockMovementRepository stockMovementRepository, HospitalRepository hospitalRepository,
                            PendingIssueRepository pendingIssueRepository, ReplenishmentService replenishmentService, AccessControlService accessControlService) {
        this.stockRepository = stockRepository;
        this.surgeryRepository = surgeryRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.hospitalRepository = hospitalRepository;
        this.pendingIssueRepository = pendingIssueRepository;
        this.replenishmentService = replenishmentService;
        this.accessControlService = accessControlService;
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

        if (hospitals.isEmpty()) {
            return new DashboardDTO(weekStart, weekEnd, 0, 0, 0, 0, 0, null, null, List.of(), List.of(), List.of());
        }

        long units = stockRepository.sumValidUnits(hospitals, today);
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

        List<StockMovementDTO> movements = stockMovementRepository
                .latest(hospitals, PageRequest.of(0, RECENT_MOVEMENTS)).stream()
                .map(StockMovementDTO::of).toList();

        return new DashboardDTO(weekStart, weekEnd, units, expiring, expired, weekSurgeries,
                pending, oldestPending, latestPending, byHospital, alerts, movements);
    }
}
