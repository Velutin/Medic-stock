package com.project.mss.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.mss.dto.replenishment.DeliveryDTO;
import com.project.mss.dto.replenishment.DeliveryFormDTO;
import com.project.mss.exception.BusinessRuleException;
import com.project.mss.exception.EntityNotFoundException;
import com.project.mss.model.entity.Delivery;
import com.project.mss.model.entity.DeliveryItem;
import com.project.mss.model.entity.Hospital;
import com.project.mss.model.entity.Lot;
import com.project.mss.model.enums.Location;
import com.project.mss.model.enums.MovementType;
import com.project.mss.repository.DeliveryRepository;

/** Deliveries: lots moved from the storeroom into the hospital they are assigned to. */
@Service
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final StockService stockService;
    private final MaterialService materialService;
    private final AccessControlService accessControlService;

    public DeliveryService(DeliveryRepository deliveryRepository, StockService stockService,
                           MaterialService materialService, AccessControlService accessControlService) {
        this.deliveryRepository = deliveryRepository;
        this.stockService = stockService;
        this.materialService = materialService;
        this.accessControlService = accessControlService;
    }

    @Transactional
    public DeliveryDTO create(DeliveryFormDTO dto) {
        accessControlService.requireManager();
        Hospital hospital = accessControlService.requireHospitalAccess(dto.hospitalId());
        LocalDate today = LocalDate.now();

        Delivery delivery = new Delivery();
        delivery.setHospital(hospital);
        delivery.setNotes(dto.notes());
        delivery.setCreatedBy(accessControlService.currentUser());
        delivery = deliveryRepository.save(delivery);

        for (DeliveryFormDTO.DeliveryRequestItem item : dto.items()) {
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
    public List<DeliveryDTO> listByHospital(Long hospitalId) {
        accessControlService.requireHospitalAccess(hospitalId);
        return deliveryRepository.findTop50ByHospitalIdOrderByCreatedAtDesc(hospitalId).stream()
                .map(DeliveryDTO::of).toList();
    }

    @Transactional(readOnly = true)
    public DeliveryDTO find(Long id) {
        return DeliveryDTO.of(load(id));
    }

    /** Loads the delivery and ensures the current user can access its hospital. */
    @Transactional(readOnly = true)
    public Delivery load(Long id) {
        Delivery e = deliveryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Delivery " + id + " not found"));
        accessControlService.requireHospitalAccess(e.getHospital().getId());
        return e;
    }
}
