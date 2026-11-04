package com.project.mss.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.mss.dto.replenishment.DeliveryDTO;
import com.project.mss.dto.replenishment.DeliveryFormDTO;
import com.project.mss.dto.replenishment.ShipmentStatusUpdateDTO;
import com.project.mss.exception.BusinessRuleException;
import com.project.mss.exception.EntityNotFoundException;
import com.project.mss.model.entity.Delivery;
import com.project.mss.model.entity.DeliveryItem;
import com.project.mss.model.entity.Hospital;
import com.project.mss.model.entity.Lot;
import com.project.mss.model.enums.Location;
import com.project.mss.model.enums.MovementType;
import com.project.mss.model.enums.ShipmentStatus;
import com.project.mss.repository.DeliveryRepository;
import com.project.mss.repository.HospitalRepository;
import com.project.mss.repository.PendingIssueRepository;

/**
 * Deliveries (replenishment): lots moved from the storeroom into a hospital. The lots are taken from the
 * hospital's own storeroom balance or, for hospitals supplied by a distribution center (e.g. SESAB),
 * from the center's storeroom balance. Both are recorded as REPLENISHMENT movements.
 */
@Service
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final HospitalRepository hospitalRepository;
    private final PendingIssueRepository pendingIssueRepository;
    private final StockService stockService;
    private final MaterialService materialService;
    private final AccessControlService accessControlService;

    public DeliveryService(DeliveryRepository deliveryRepository, HospitalRepository hospitalRepository,
                           PendingIssueRepository pendingIssueRepository,
                           StockService stockService, MaterialService materialService,
                           AccessControlService accessControlService) {
        this.deliveryRepository = deliveryRepository;
        this.hospitalRepository = hospitalRepository;
        this.pendingIssueRepository = pendingIssueRepository;
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
        delivery.setStatus(ShipmentStatus.DISPATCHED);
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
            // Into the road, not into the hospital: the box still has to get there, and until someone
            // confirms it the hospital does not have this material. The type stays REPLENISHMENT so the
            // movements report keeps counting one transfer per transfer.
            stockService.transfer(MovementType.REPLENISHMENT, lot, item.quantity(),
                    source, Location.STOREROOM, hospital, Location.IN_TRANSIT,
                    null, delivery.getId(), "Delivery #" + delivery.getId());
            delivery.getItems().add(new DeliveryItem(delivery, lot, item.quantity()));
        }
        return DeliveryDTO.of(deliveryRepository.save(delivery));
    }

    /**
     * Confirms that the box reached the hospital.
     *
     * A line nobody mentions counts as fully received, which is the ordinary case: the box arrives whole and
     * you confirm it in one gesture. What came short does not wait for a second delivery - a shipment is one
     * box - so its units stay on the road and leave a pending issue behind, to be settled by hand.
     */
    /**
     * Single state change of a shipment, as the surgery does it: the body says where it should go.
     * A transfer that is on the road either arrives or is called off; there is nothing else to do to it.
     */
    @Transactional
    public DeliveryDTO updateStatus(Long id, ShipmentStatusUpdateDTO dto) {
        return switch (dto.status()) {
            case ARRIVED -> arrive(id, dto);
            case CANCELLED -> cancel(id);
            case DISPATCHED -> throw new BusinessRuleException(
                    "A shipment is dispatched when it is created; it cannot be sent back to the road");
        };
    }

    private DeliveryDTO arrive(Long id, ShipmentStatusUpdateDTO dto) {
        accessControlService.requireManager();
        Delivery delivery = load(id);
        if (delivery.getStatus() != ShipmentStatus.DISPATCHED) {
            throw new BusinessRuleException("Transfer " + id + " is not on the road: it is "
                    + delivery.getStatus());
        }
        LocalDate day = ShipmentArrival.arrivalDay(dto, delivery.getCreatedAt());
        Map<Long, Integer> informed = ShipmentArrival.informedQuantities(dto);

        for (DeliveryItem item : delivery.getItems()) {
            int sent = item.getQuantity();
            int received = informed.getOrDefault(item.getId(), sent);
            ShipmentArrival.requireWithinSent(received, sent, item.getLot().getNumber());
            if (received > 0) {
                stockService.recordShipmentArrival(item.getLot(), received, delivery.getHospital(), null,
                        delivery.getId(), "Transfer #" + delivery.getId() + " received");
            }
            item.setArrivedQuantity(received);
            if (received < sent) {
                pendingIssueRepository.save(ShipmentArrival.notDelivered(delivery.getHospital(), item.getLot(),
                        sent - received, item, null));
            }
        }
        delivery.setStatus(ShipmentStatus.ARRIVED);
        delivery.setArrivedOn(day);
        delivery.setArrivedBy(accessControlService.currentUser());
        return DeliveryDTO.of(deliveryRepository.save(delivery));
    }

    /**
     * Calls off a transfer that never left, putting the material back in the storeroom it came from.
     *
     * Only while nothing has been confirmed. Once the box has been opened at the hospital there is nothing
     * to call off: what is there is there, and what is missing is already a pending issue.
     */
    private DeliveryDTO cancel(Long id) {
        accessControlService.requireManager();
        Delivery delivery = load(id);
        if (delivery.getStatus() != ShipmentStatus.DISPATCHED) {
            throw new BusinessRuleException("Only a transfer still on the road can be called off");
        }
        Hospital source = delivery.getSourceHospital() != null ? delivery.getSourceHospital() : delivery.getHospital();
        for (DeliveryItem item : delivery.getItems()) {
            stockService.recordShipmentReturn(item.getLot(), item.getQuantity(), delivery.getHospital(),
                    source, Location.STOREROOM, null, delivery.getId(),
                    "Transfer #" + delivery.getId() + " called off");
        }
        delivery.setStatus(ShipmentStatus.CANCELLED);
        return DeliveryDTO.of(deliveryRepository.save(delivery));
    }

    /** Transfers still on the road, newest first. */
    @Transactional(readOnly = true)
    public List<DeliveryDTO> listDispatched() {
        accessControlService.requireManager();
        return deliveryRepository.findByStatusOrderByCreatedAtDesc(ShipmentStatus.DISPATCHED).stream()
                .filter(d -> accessControlService.allowedHospitals().contains(d.getHospital().getId()))
                .map(DeliveryDTO::of).toList();
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
