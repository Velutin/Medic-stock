package com.project.mss.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.mss.dto.replenishment.ReplenishmentSuggestionDTO;
import com.project.mss.dto.replenishment.StockLevelDTO;
import com.project.mss.model.entity.Hospital;
import com.project.mss.model.entity.Material;
import com.project.mss.model.entity.MinimumStock;
import com.project.mss.model.entity.ProductSection;
import com.project.mss.model.entity.Stock;
import com.project.mss.model.enums.Location;
import com.project.mss.repository.HospitalRepository;
import com.project.mss.repository.MaterialRepository;
import com.project.mss.repository.MinimumStockRepository;
import com.project.mss.repository.StockRepository;

/**
 * Per-hospital replenishment:
 *  - below the hospital "Ideal" -> replenish from the storeroom (lots assigned to that hospital);
 *  - below the "Ideal total" (hospital + storeroom) -> order material from the supplier.
 * Expired lots are ignored in both calculations.
 * Deliveries and supplier orders are handled by DeliveryService and SupplierOrderService.
 */
@Service
public class ReplenishmentService {

    private final MinimumStockRepository minimumStockRepository;
    private final StockRepository stockRepository;
    private final HospitalRepository hospitalRepository;
    private final AccessControlService accessControlService;
    private final MaterialRepository materialRepository;

    public ReplenishmentService(MinimumStockRepository minimumStockRepository, StockRepository stockRepository,
                                HospitalRepository hospitalRepository, AccessControlService accessControlService,
                                MaterialRepository materialRepository) {
        this.minimumStockRepository = minimumStockRepository;
        this.stockRepository = stockRepository;
        this.hospitalRepository = hospitalRepository;
        this.accessControlService = accessControlService;
        this.materialRepository = materialRepository;
    }

    // ============================================================ suggestion

    /**
     * What to replenish and what to order, per REF with minimum levels in the hospital. Expired lots are not counted.
     * For a hospital supplied by a distribution center, the storeroom is the center's and supplier orders are left
     * to the center (orderFromSupplier = 0). For a distribution center, only the ideal total matters: it is compared
     * with the center's storeroom plus the stock inside the hospitals it supplies.
     */
    @Transactional(readOnly = true)
    public List<ReplenishmentSuggestionDTO> suggestion(Long hospitalId, boolean onlyWithShortage) {
        Hospital hospital = accessControlService.requireHospitalAccess(hospitalId);
        Hospital center = hospitalRepository.findCenterOf(hospitalId).orElse(null);
        Long storeroomHospitalId = center != null ? center.getId() : hospitalId;
        LocalDate today = LocalDate.now();
        Map<Long, Integer> inside = validBalances(insideHospitals(hospital), Location.HOSPITAL, today);
        Map<Long, Integer> storeroom = validBalances(List.of(storeroomHospitalId), Location.STOREROOM, today);
        List<ReplenishmentSuggestionDTO> result = new ArrayList<>();

        for (MinimumStock min : minimumStockRepository.listByHospital(hospitalId)) {
            Material m = min.getMaterial();
            int hospitalBalance = inside.getOrDefault(m.getId(), 0);
            int storeroomBalance = storeroom.getOrDefault(m.getId(), 0);

            int hospitalShortage = hospital.isDistributionCenter() ? 0 : Math.max(0, min.getHospitalIdeal() - hospitalBalance);
            int replenishFromStoreroom = Math.min(hospitalShortage, storeroomBalance);
            int orderFromSupplier = center != null ? 0
                    : Math.max(0, min.getIdealTotal() - (hospitalBalance + storeroomBalance));

            if (onlyWithShortage && replenishFromStoreroom == 0 && orderFromSupplier == 0) continue;

            List<ReplenishmentSuggestionDTO.SuggestedLot> lots = new ArrayList<>();
            int remaining = replenishFromStoreroom;
            if (remaining > 0) {
                for (Stock e : stockRepository.listByMaterialFefo(storeroomHospitalId, m.getId(), Location.STOREROOM)) {
                    if (remaining <= 0) break;
                    if (e.getLot().isExpired(today)) continue;
                    int q = Math.min(remaining, e.getQuantity());
                    lots.add(new ReplenishmentSuggestionDTO.SuggestedLot(e.getLot().getId(), e.getLot().getNumber(),
                            e.getLot().getExpiryDate(), q));
                    remaining -= q;
                }
            }

            result.add(new ReplenishmentSuggestionDTO(m.getId(), m.getRef(), m.getDescription(),
                    min.getHospitalIdeal(), min.getIdealTotal(), hospitalBalance, storeroomBalance,
                    replenishFromStoreroom, orderFromSupplier, storeroomHospitalId, lots));
        }
        return result;
    }

    /**
     * Minimum levels and balances of every REF the hospital may work with: the catalog items of its product lines,
     * the REFs with minimum levels and the REFs with balance inside it. Used to edit the minimums on screen.
     */
    @Transactional(readOnly = true)
    public List<StockLevelDTO> levels(Long hospitalId) {
        accessControlService.requireManager();
        Hospital hospital = accessControlService.requireHospitalAccess(hospitalId);
        Long storeroomHospitalId = hospitalRepository.findCenterOf(hospitalId).map(Hospital::getId).orElse(hospitalId);
        LocalDate today = LocalDate.now();
        Map<Long, Integer> inside = validBalances(insideHospitals(hospital), Location.HOSPITAL, today);
        Map<Long, Integer> storeroom = validBalances(List.of(storeroomHospitalId), Location.STOREROOM, today);
        Map<Long, MinimumStock> minimums = new HashMap<>();
        minimumStockRepository.listByHospital(hospitalId).forEach(min -> minimums.put(min.getMaterial().getId(), min));

        return materialRepository.findAll().stream()
                .filter(m -> minimums.containsKey(m.getId()) || inside.getOrDefault(m.getId(), 0) > 0
                        || (Boolean.TRUE.equals(m.getActive())
                            && m.getProductLines().stream().anyMatch(hospital.getProductLines()::contains)))
                .sorted(Comparator.comparing(Material::getRef))
                .map(m -> {
                    MinimumStock min = minimums.get(m.getId());
                    ProductSection s = m.getSection();
                    return new StockLevelDTO(m.getId(), m.getRef(), m.getComponent(), m.getDescription(), m.getSize(),
                            m.getColor(), s == null ? null : s.getId(), s == null ? null : s.getName(),
                            s == null ? null : s.getDisplayOrder(),
                            min == null ? 0 : min.getHospitalIdeal(), min == null ? 0 : min.getIdealTotal(),
                            inside.getOrDefault(m.getId(), 0), storeroom.getOrDefault(m.getId(), 0));
                })
                .toList();
    }

    /** Where "inside the hospital" is counted: the hospital itself, or the hospitals a distribution center supplies. */
    private List<Long> insideHospitals(Hospital hospital) {
        if (!hospital.isDistributionCenter()) return List.of(hospital.getId());
        return hospital.getCoveredHospitals().stream().map(Hospital::getId).toList();
    }

    /** Valid balance per material in the given hospitals and location. */
    private Map<Long, Integer> validBalances(List<Long> hospitalIds, Location location, LocalDate today) {
        Map<Long, Integer> out = new HashMap<>();
        for (Long id : hospitalIds) {
            for (Stock e : stockRepository.listByHospital(id)) {
                if (e.getLocation() != location || e.getQuantity() <= 0 || e.getLot().isExpired(today)) continue;
                out.merge(e.getLot().getMaterial().getId(), e.getQuantity(), Integer::sum);
            }
        }
        return out;
    }
}
