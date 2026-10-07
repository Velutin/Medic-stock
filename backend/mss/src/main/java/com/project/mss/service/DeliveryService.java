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
import com.project.mss.repository.HospitalRepository;

/**
 * Deliveries (replenishment): lots moved from the storeroom into a hospital. The lots are taken from the
 * hospital's own storeroom balance or, for hospitals supplied by a distribution center (e.g. SESAB),
 * from the center's storeroom balance. Both are recorded as REPLENISHMENT movements.
 */
@Service
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final HospitalRepository hospitalRepository;
    private final StockService stockService;
    private final MaterialService materialService;
    private final AccessControlService accessControlService;

    public DeliveryService(DeliveryRepository deliveryRepository, HospitalRepository hospitalRepository,
                           StockService stockService, MaterialService materialService,
                           AccessControlService accessControlService) {
        this.deliveryRepository = deliveryRepository;
        this.hospitalRepository = hospitalRepository;
        this.stockService = stockService;
        this.materialService = materialService;
        this.accessControlService = accessControlService;
    }

    @Transactional
    public DeliveryDTO create(DeliveryFormDTO dto) {
        accessControlService.requireManager();
        Hospital hospital = accessControlService.requireHospitalAccess(dto.hospitalId());
        if (hospital.isDistributionCenter()) {
            throw new BusinessRuleException(hospital.getName()
                    + " is a distribution center: deliver to one of the hospitals it supplies");
        }
        Hospital source = resolveSource(hospital, dto.sourceHospitalId());
        LocalDate today = LocalDate.now();

        Delivery delivery = new Delivery();
        delivery.setHospital(hospital);
        delivery.setSourceHospital(source == hospital ? null : source);
        delivery.setNotes(dto.notes());
        delivery.setCreatedBy(accessControlService.currentUser());
        delivery = deliveryRepository.save(delivery);

        for (DeliveryFormDTO.DeliveryRequestItem item : dto.items()) {
            Lot lot = materialService.findLot(item.lotId());
            if (lot.isExpired(today)) {
                throw new BusinessRuleException("Lot " + lot.getNumber() + " is expired and cannot be delivered");
            }
            stockService.transfer(MovementType.REPLENISHMENT, lot, item.quantity(),
                    source, Location.STOREROOM, hospital, Location.HOSPITAL,
                    null, delivery.getId(), "Delivery #" + delivery.getId());
            delivery.getItems().add(new DeliveryItem(delivery, lot, item.quantity()));
        }
        return DeliveryDTO.of(deliveryRepository.save(delivery));
    }

    /**
     * Storeroom owner of the lots. When not informed, it is the distribution center that supplies
     * the hospital (e.g. SESAB) or, for regular hospitals, the hospital itself. Informing the hospital's
     * own id forces its own storeroom, even for a hospital supplied by a center.
     */
    private Hospital resolveSource(Hospital hospital, Long sourceHospitalId) {
        if (sourceHospitalId == null) {
            return hospitalRepository.findCenterOf(hospital.getId()).orElse(hospital);
        }
        if (sourceHospitalId.equals(hospital.getId())) {
            return hospital;
        }
        Hospital center = accessControlService.requireHospitalAccess(sourceHospitalId);
        if (!center.isDistributionCenter()) {
            throw new BusinessRuleException(center.getName() + " is not a distribution center");
        }
        if (center.getCoveredHospitals().stream().noneMatch(h -> h.getId().equals(hospital.getId()))) {
            throw new BusinessRuleException(hospital.getName() + " is not supplied by " + center.getName());
        }
        return center;
    }

    /** Latest deliveries to a hospital. */
    @Transactional(readOnly = true)
    public List<DeliveryDTO> listByHospital(Long hospitalId) {
        accessControlService.requireHospitalAccess(hospitalId);
        return deliveryRepository.findTop50ByHospitalIdOrderByCreatedAtDesc(hospitalId).stream()
                .map(DeliveryDTO::of).toList();
    }

    /** Latest deliveries made from a distribution center storeroom. */
    @Transactional(readOnly = true)
    public List<DeliveryDTO> listBySource(Long sourceHospitalId) {
        accessControlService.requireHospitalAccess(sourceHospitalId);
        return deliveryRepository.findTop50BySourceHospitalIdOrderByCreatedAtDesc(sourceHospitalId).stream()
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
