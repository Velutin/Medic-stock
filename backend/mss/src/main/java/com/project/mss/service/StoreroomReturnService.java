package com.project.mss.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.mss.dto.replenishment.StoreroomReturnDTO;
import com.project.mss.dto.replenishment.StoreroomReturnFormDTO;
import com.project.mss.exception.BusinessRuleException;
import com.project.mss.exception.EntityNotFoundException;
import com.project.mss.model.entity.Hospital;
import com.project.mss.model.entity.Lot;
import com.project.mss.model.entity.StoreroomReturn;
import com.project.mss.model.entity.StoreroomReturnItem;
import com.project.mss.repository.HospitalRepository;
import com.project.mss.repository.StoreroomReturnRepository;

/**
 * Withdrawals from a hospital back into the storeroom: the opposite way of {@link DeliveryService}.
 *
 * The material leaves the stock inside the hospital and enters the storeroom balance of its owner - the
 * hospital itself or, for a hospital supplied by a distribution center, the center, since a covered hospital
 * has no storeroom of its own. Expired lots are allowed on purpose: clearing expired material out of a
 * hospital is the main reason this exists, and refusing it would leave that material stuck inside.
 */
@Service
public class StoreroomReturnService {

    private final StoreroomReturnRepository storeroomReturnRepository;
    private final HospitalRepository hospitalRepository;
    private final StockService stockService;
    private final MaterialService materialService;
    private final AccessControlService accessControlService;

    public StoreroomReturnService(StoreroomReturnRepository storeroomReturnRepository,
                                  HospitalRepository hospitalRepository, StockService stockService,
                                  MaterialService materialService, AccessControlService accessControlService) {
        this.storeroomReturnRepository = storeroomReturnRepository;
        this.hospitalRepository = hospitalRepository;
        this.stockService = stockService;
        this.materialService = materialService;
        this.accessControlService = accessControlService;
    }

    @Transactional
    public StoreroomReturnDTO create(StoreroomReturnFormDTO dto) {
        accessControlService.requireManager();
        Hospital hospital = accessControlService.requireHospitalAccess(dto.hospitalId());
        if (hospital.isDistributionCenter()) {
            throw new BusinessRuleException(hospital.getName()
                    + " is a distribution center: its material is already in the storeroom");
        }
        // Where the material goes back to. Not informed by the screen: a covered hospital can only return to
        // the center that supplies it, and a regular hospital to its own storeroom.
        Hospital storeroom = hospitalRepository.findCenterOf(hospital.getId()).orElse(hospital);

        StoreroomReturn e = new StoreroomReturn();
        e.setHospital(hospital);
        e.setStoreroomHospital(storeroom);
        e.setReason(dto.reason().trim());
        e.setNotes(dto.notes());
        e.setCreatedBy(accessControlService.currentUser());
        e = storeroomReturnRepository.save(e);

        for (StoreroomReturnFormDTO.StoreroomReturnRequestItem item : dto.items()) {
            Lot lot = materialService.findLot(item.lotId());
            // No expiry check here, unlike a delivery: expired material is exactly what this takes out.
            stockService.recordStoreroomReturn(lot, item.quantity(), hospital, storeroom, e.getId(),
                    "Withdrawal to storeroom #" + e.getId() + ": " + e.getReason());
            e.getItems().add(new StoreroomReturnItem(e, lot, item.quantity()));
        }
        return StoreroomReturnDTO.of(storeroomReturnRepository.save(e));
    }

    /** Latest withdrawals from a hospital. */
    @Transactional(readOnly = true)
    public List<StoreroomReturnDTO> listByHospital(Long hospitalId) {
        accessControlService.requireHospitalAccess(hospitalId);
        return storeroomReturnRepository.findTop50ByHospitalIdOrderByCreatedAtDesc(hospitalId).stream()
                .map(StoreroomReturnDTO::of).toList();
    }

    @Transactional(readOnly = true)
    public StoreroomReturnDTO find(Long id) {
        return StoreroomReturnDTO.of(load(id));
    }

    /** Entity with the hospital access already checked, for the report. */
    @Transactional(readOnly = true)
    public StoreroomReturn load(Long id) {
        StoreroomReturn e = storeroomReturnRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Withdrawal " + id + " not found"));
        accessControlService.requireHospitalAccess(e.getHospital().getId());
        return e;
    }
}
