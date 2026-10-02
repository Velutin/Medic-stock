package com.project.mss.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.mss.dto.replenishment.MinimumStockDTO;
import com.project.mss.dto.replenishment.MinimumStockFormDTO;
import com.project.mss.dto.replenishment.MinimumStockPatchDTO;
import com.project.mss.exception.BusinessRuleException;
import com.project.mss.exception.EntityNotFoundException;
import com.project.mss.model.entity.Hospital;
import com.project.mss.model.entity.Material;
import com.project.mss.model.entity.MinimumStock;
import com.project.mss.repository.MaterialRepository;
import com.project.mss.repository.MinimumStockRepository;

/**
 * Minimum levels per hospital and REF, used by the replenishment suggestion:
 *  - hospitalIdeal: minimum inside the hospital (below it, replenish from the storeroom);
 *  - idealTotal: minimum across hospital + storeroom (below it, order from the supplier).
 * A distribution center has no stock inside a hospital, so only its idealTotal is used (hospitalIdeal = 0).
 */
@Service
public class MinimumStockService {

    private final MinimumStockRepository minimumStockRepository;
    private final MaterialRepository materialRepository;
    private final AccessControlService accessControlService;

    public MinimumStockService(MinimumStockRepository minimumStockRepository, MaterialRepository materialRepository,
                               AccessControlService accessControlService) {
        this.minimumStockRepository = minimumStockRepository;
        this.materialRepository = materialRepository;
        this.accessControlService = accessControlService;
    }

   @Transactional(readOnly = true)
    public List<MinimumStockDTO> list(Long hospitalId) {
        accessControlService.requireManager();
        accessControlService.requireHospitalAccess(hospitalId);
        return minimumStockRepository.listByHospital(hospitalId).stream().map(MinimumStockDTO::of).toList();
    }
    
    /** Creates or replaces the minimum levels of a REF (idempotent). */
    @Transactional
    public MinimumStockDTO put(Long hospitalId, Long materialId, MinimumStockFormDTO dto) {
        accessControlService.requireManager();
        Hospital hospital = accessControlService.requireHospitalAccess(hospitalId);
        Material material = materialRepository.findById(materialId)
                .orElseThrow(() -> new EntityNotFoundException("Material " + materialId + " not found"));
        MinimumStock min = minimumStockRepository.findByHospitalIdAndMaterialId(hospitalId, materialId)
                .orElseGet(() -> {
                    MinimumStock created = new MinimumStock();
                    created.setHospital(hospital);
                    created.setMaterial(material);
                    return created;
                });
        apply(min, hospital, dto.hospitalIdeal(), dto.idealTotal());
        return MinimumStockDTO.of(minimumStockRepository.save(min));
    }

    /** Changes only the informed levels of an existing REF in the list. */
    @Transactional
    public MinimumStockDTO patch(Long hospitalId, Long materialId, MinimumStockPatchDTO dto) {
        accessControlService.requireManager();
        Hospital hospital = accessControlService.requireHospitalAccess(hospitalId);
        MinimumStock min = load(hospitalId, materialId);
        apply(min, hospital,
                dto.hospitalIdeal() != null ? dto.hospitalIdeal() : min.getHospitalIdeal(),
                dto.idealTotal() != null ? dto.idealTotal() : min.getIdealTotal());
        return MinimumStockDTO.of(minimumStockRepository.save(min));
    }

    /** Removes the REF from the hospital list: it is no longer considered in the replenishment suggestion. */
    @Transactional
    public void delete(Long hospitalId, Long materialId) {
        accessControlService.requireManager();
        accessControlService.requireHospitalAccess(hospitalId);
        minimumStockRepository.delete(load(hospitalId, materialId));
    }

    /** Shared rules for every change of minimum levels (also used by the spreadsheet import). */
    public static void validate(Hospital hospital, int hospitalIdeal, int idealTotal) {
        if (hospitalIdeal < 0 || idealTotal < 0) {
            throw new BusinessRuleException("Minimum levels cannot be negative");
        }
        if (idealTotal < hospitalIdeal) {
            throw new BusinessRuleException("Ideal total cannot be lower than the hospital ideal");
        }
        if (hospital.isDistributionCenter() && hospitalIdeal > 0) {
            throw new BusinessRuleException(hospital.getName()
                    + " is a distribution center: only the ideal total is used (hospital ideal must be 0)");
        }
    }

    private void apply(MinimumStock min, Hospital hospital, int hospitalIdeal, int idealTotal) {
        validate(hospital, hospitalIdeal, idealTotal);
        min.setHospitalIdeal(hospitalIdeal);
        min.setIdealTotal(idealTotal);
    }

    private MinimumStock load(Long hospitalId, Long materialId) {
        return minimumStockRepository.findByHospitalIdAndMaterialId(hospitalId, materialId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Material " + materialId + " is not in the minimum list of hospital " + hospitalId));
    }
}
