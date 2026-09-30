package com.project.mss.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.mss.dto.replenishment.DeliveryDTO;
import com.project.mss.dto.replenishment.ExecuteReplenishmentDTO;
import com.project.mss.dto.replenishment.SupplierOrderDTO;
import com.project.mss.dto.replenishment.SupplierOrderFormDTO;
import com.project.mss.dto.replenishment.ReplenishmentSuggestionDTO;
import com.project.mss.exception.BusinessRuleException;
import com.project.mss.exception.EntityNotFoundException;
import com.project.mss.model.entity.Delivery;
import com.project.mss.model.entity.DeliveryItem;
import com.project.mss.model.entity.Stock;
import com.project.mss.model.entity.MinimumStock;
import com.project.mss.model.entity.Hospital;
import com.project.mss.model.entity.Lot;
import com.project.mss.model.entity.Material;
import com.project.mss.model.entity.SupplierOrder;
import com.project.mss.model.entity.SupplierOrderItem;
import com.project.mss.model.enums.Location;
import com.project.mss.model.enums.OrderStatus;
import com.project.mss.model.enums.MovementType;
import com.project.mss.repository.DeliveryRepository;
import com.project.mss.repository.MinimumStockRepository;
import com.project.mss.repository.StockRepository;
import com.project.mss.repository.MaterialRepository;
import com.project.mss.repository.SupplierOrderRepository;

/**
 * Per-hospital replenishment:
 *  - below the hospital "Ideal" -> replenish from the storeroom (lots assigned to that hospital);
 *  - below the "Ideal total" (hospital + storeroom) -> order material from the supplier.
 * Expired lots are ignored in both calculations.
 */
@Service
public class ReplenishmentService {

    private final MinimumStockRepository minimumStockRepository;
    private final StockRepository stockRepository;
    private final DeliveryRepository deliveryRepository;
    private final SupplierOrderRepository supplierOrderRepository;
    private final MaterialRepository materialRepository;
    private final StockService stockService;
    private final MaterialService materialService;
    private final AccessControlService accessControlService;

    public ReplenishmentService(MinimumStockRepository minimumStockRepository, StockRepository stockRepository,
                            DeliveryRepository deliveryRepository, SupplierOrderRepository supplierOrderRepository,
                            MaterialRepository materialRepository, StockService stockService,
                            MaterialService materialService, AccessControlService accessControlService) {
        this.minimumStockRepository = minimumStockRepository;
        this.stockRepository = stockRepository;
        this.deliveryRepository = deliveryRepository;
        this.supplierOrderRepository = supplierOrderRepository;
        this.materialRepository = materialRepository;
        this.stockService = stockService;
        this.materialService = materialService;
        this.accessControlService = accessControlService;
    }

    // ============================================================ suggestion

    @Transactional(readOnly = true)
    public List<ReplenishmentSuggestionDTO> suggestion(Long hospitalId, boolean onlyWithShortage) {
        accessControlService.requireHospitalAccess(hospitalId);
        LocalDate today = LocalDate.now();
        List<ReplenishmentSuggestionDTO> result = new ArrayList<>();

        for (MinimumStock min : minimumStockRepository.listByHospital(hospitalId)) {
            Material m = min.getMaterial();
            List<Stock> noHospital = stockRepository.listByMaterialFefo(hospitalId, m.getId(), Location.HOSPITAL);
            List<Stock> inStoreroom = stockRepository.listByMaterialFefo(hospitalId, m.getId(), Location.STOREROOM);

            int hospitalBalance = sumValid(noHospital, today);
            int storeroomBalance = sumValid(inStoreroom, today);

            int hospitalShortage = Math.max(0, min.getHospitalIdeal() - hospitalBalance);
            int replenishFromStoreroom = Math.min(hospitalShortage, storeroomBalance);
            int orderFromSupplier = Math.max(0, min.getIdealTotal() - (hospitalBalance + storeroomBalance));

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
                    replenishFromStoreroom, orderFromSupplier, lots));
        }
        return result;
    }

    // ============================================================ storeroom -> hospital

    @Transactional
    public DeliveryDTO executeReplenishment(ExecuteReplenishmentDTO dto) {
        accessControlService.requireManager();
        Hospital hospital = accessControlService.requireHospitalAccess(dto.hospitalId());
        LocalDate today = LocalDate.now();

        Delivery delivery = new Delivery();
        delivery.setHospital(hospital);
        delivery.setNotes(dto.notes());
        delivery.setCreatedBy(accessControlService.currentUser());
        delivery = deliveryRepository.save(delivery);

        for (ExecuteReplenishmentDTO.ReplenishmentRequestItem item : dto.items()) {
            Lot lot = materialService.findLot(item.lotId());
            if (lot.isExpired(today)) {
                throw new BusinessRuleException("Lot " + lot.getNumber() + " is expired and cannot be delivered");
            }
            stockService.transfer(MovementType.REPLENISHMENT, lot, item.quantity(),
                    hospital, Location.STOREROOM, hospital, Location.HOSPITAL,
                    null, delivery.getId(), "Delivery #" + delivery.getId());
            delivery.getItems().add(new DeliveryItem(delivery, lot, item.quantity()));
        }
        return DeliveryDTO.of(deliveryRepository.save(delivery));
    }

    @Transactional(readOnly = true)
    public List<DeliveryDTO> listDeliveries(Long hospitalId) {
        accessControlService.requireHospitalAccess(hospitalId);
        return deliveryRepository.findTop50ByHospitalIdOrderByCreatedAtDesc(hospitalId).stream()
                .map(DeliveryDTO::of).toList();
    }

    @Transactional(readOnly = true)
    public Delivery loadDelivery(Long id) {
        Delivery e = deliveryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Delivery " + id + " not found"));
        accessControlService.requireHospitalAccess(e.getHospital().getId());
        return e;
    }

    // ============================================================ supplier order

    @Transactional
    public SupplierOrderDTO generateOrder(SupplierOrderFormDTO dto) {
        accessControlService.requireManager();
        Hospital hospital = accessControlService.requireHospitalAccess(dto.hospitalId());

        SupplierOrder p = new SupplierOrder();
        p.setHospital(hospital);
        p.setNotes(dto.notes());
        p.setCreatedBy(accessControlService.currentUser());
        for (SupplierOrderFormDTO.SupplierOrderRequestItem i : dto.items()) {
            Material m = materialRepository.findById(i.materialId())
                    .orElseThrow(() -> new EntityNotFoundException("Material " + i.materialId() + " not found"));
            SupplierOrderItem item = new SupplierOrderItem();
            item.setSupplierOrder(p);
            item.setMaterial(m);
            item.setQuantity(i.quantity());
            item.setUrgent(i.urgent() == null || i.urgent());
            p.getItems().add(item);
        }
        return SupplierOrderDTO.of(supplierOrderRepository.save(p));
    }

    @Transactional
    public SupplierOrderDTO markSent(Long id) {
        accessControlService.requireManager();
        SupplierOrder p = loadOrder(id);
        p.setStatus(OrderStatus.SENT);
        p.setSentAt(LocalDateTime.now());
        return SupplierOrderDTO.of(supplierOrderRepository.save(p));
    }

    @Transactional(readOnly = true)
    public List<SupplierOrderDTO> listOrders(Long hospitalId) {
        accessControlService.requireHospitalAccess(hospitalId);
        return supplierOrderRepository.findTop50ByHospitalIdOrderByCreatedAtDesc(hospitalId).stream()
                .map(SupplierOrderDTO::of).toList();
    }

    @Transactional(readOnly = true)
    public SupplierOrder loadOrder(Long id) {
        SupplierOrder p = supplierOrderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order " + id + " not found"));
        accessControlService.requireHospitalAccess(p.getHospital().getId());
        return p;
    }

    private int sumValid(List<Stock> items, LocalDate today) {
        return items.stream().filter(e -> !e.getLot().isExpired(today)).mapToInt(Stock::getQuantity).sum();
    }
}
