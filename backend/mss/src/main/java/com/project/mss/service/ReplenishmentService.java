package com.project.mss.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.mss.dto.replenishment.ReplenishmentSuggestionDTO;
import com.project.mss.model.entity.Hospital;
import com.project.mss.model.entity.Material;
import com.project.mss.model.entity.MinimumStock;
import com.project.mss.model.entity.Stock;
import com.project.mss.model.enums.Location;
import com.project.mss.repository.HospitalRepository;
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

    public ReplenishmentService(MinimumStockRepository minimumStockRepository, StockRepository stockRepository,
                                HospitalRepository hospitalRepository, AccessControlService accessControlService) {
        this.minimumStockRepository = minimumStockRepository;
        this.stockRepository = stockRepository;
        this.hospitalRepository = hospitalRepository;
        this.accessControlService = accessControlService;
    }

    // ============================================================ suggestion

    /**
     * For a regular hospital, the storeroom is its own. For a hospital supplied by a distribution
     * center, the storeroom is the center's and supplier orders are left to the center (orderFromSupplier = 0).
     * For a distribution center, only the ideal total matters (set the hospital ideal to 0).
     */
    @Transactional(readOnly = true)
    public List<ReplenishmentSuggestionDTO> suggestion(Long hospitalId, boolean onlyWithShortage) {
        accessControlService.requireHospitalAccess(hospitalId);
        Hospital center = hospitalRepository.findCenterOf(hospitalId).orElse(null);
        Long storeroomHospitalId = center != null ? center.getId() : hospitalId;
        LocalDate today = LocalDate.now();
        List<ReplenishmentSuggestionDTO> result = new ArrayList<>();

        for (MinimumStock min : minimumStockRepository.listByHospital(hospitalId)) {
            Material m = min.getMaterial();
            List<Stock> noHospital = stockRepository.listByMaterialFefo(hospitalId, m.getId(), Location.HOSPITAL);
            List<Stock> inStoreroom = stockRepository.listByMaterialFefo(storeroomHospitalId, m.getId(), Location.STOREROOM);

            int hospitalBalance = sumValid(noHospital, today);
            int storeroomBalance = sumValid(inStoreroom, today);

            int hospitalShortage = Math.max(0, min.getHospitalIdeal() - hospitalBalance);
            int replenishFromStoreroom = Math.min(hospitalShortage, storeroomBalance);
            int orderFromSupplier = center != null ? 0
                    : Math.max(0, min.getIdealTotal() - (hospitalBalance + storeroomBalance));

            if (onlyWithShortage && replenishFromStoreroom == 0 && orderFromSupplier == 0) continue;

            List<ReplenishmentSuggestionDTO.SuggestedLot> lots = new ArrayList<>();
            int remaining = replenishFromStoreroom;
            for (Stock e : inStoreroom) {
                if (remaining <= 0) break;
                if (e.getLot().isExpired(today)) continue;
                int q = Math.min(remaining, e.getQuantity());
                lots.add(new ReplenishmentSuggestionDTO.SuggestedLot(e.getLot().getId(), e.getLot().getNumber(),
                        e.getLot().getExpiryDate(), q));
                remaining -= q;
            }

            result.add(new ReplenishmentSuggestionDTO(m.getId(), m.getRef(), m.getDescription(),
                    min.getHospitalIdeal(), min.getIdealTotal(), hospitalBalance, storeroomBalance,
                    replenishFromStoreroom, orderFromSupplier, storeroomHospitalId, lots));
        }
        return result;
    }

    private int sumValid(List<Stock> items, LocalDate today) {
        return items.stream().filter(e -> !e.getLot().isExpired(today)).mapToInt(Stock::getQuantity).sum();
    }
}
