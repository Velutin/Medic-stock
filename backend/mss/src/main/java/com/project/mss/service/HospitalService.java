package com.project.mss.service;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.mss.dto.hospital.CoveredHospitalsDTO;
import com.project.mss.dto.hospital.HospitalDTO;
import com.project.mss.dto.hospital.HospitalFormDTO;
import com.project.mss.exception.BusinessRuleException;
import com.project.mss.exception.EntityNotFoundException;
import com.project.mss.model.entity.Hospital;
import com.project.mss.model.enums.HospitalType;
import com.project.mss.repository.HospitalRepository;

@Service
public class HospitalService {

    private final HospitalRepository hospitalRepository;
    private final AccessControlService accessControlService;

    public HospitalService(HospitalRepository hospitalRepository, AccessControlService accessControlService) {
        this.hospitalRepository = hospitalRepository;
        this.accessControlService = accessControlService;
    }

    @Transactional(readOnly = true)
    public List<HospitalDTO> listVisible(boolean includeInactive) {
        if (includeInactive) {
            accessControlService.requireManager();
            return hospitalRepository.findAllByOrderByNameAsc().stream().map(HospitalDTO::of).toList();
        }
        var allowed = accessControlService.allowedHospitals();
        return hospitalRepository.findByActiveTrueOrderByNameAsc().stream()
                .filter(h -> allowed.contains(h.getId()))
                .map(HospitalDTO::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public HospitalDTO find(Long id) {
        return HospitalDTO.of(accessControlService.requireHospitalAccess(id));
    }

    @Transactional
    public HospitalDTO create(HospitalFormDTO dto) {
        if (hospitalRepository.existsByNameIgnoreCase(dto.name().trim())) {
            throw new BusinessRuleException("A hospital already exists with the name " + dto.name());
        }
        Hospital h = new Hospital();
        h.setType(dto.type() == null ? HospitalType.HOSPITAL : dto.type());
        apply(h, dto);
        return HospitalDTO.of(hospitalRepository.save(h));
    }

    @Transactional
    public HospitalDTO update(Long id, HospitalFormDTO dto) {
        Hospital h = findEntity(id);
        apply(h, dto);
        return HospitalDTO.of(hospitalRepository.save(h));
    }


    /**
     * Sets the hospitals supplied by a distribution center. A hospital can belong to only one center
     * and must be a regular hospital.
     */
    @Transactional
    public HospitalDTO setCoveredHospitals(Long centerId, CoveredHospitalsDTO dto) {
        accessControlService.requireManager();
        Hospital center = findEntity(centerId);
        if (!center.isDistributionCenter()) {
            throw new BusinessRuleException(center.getName() + " is not a distribution center");
        }
        var hospitals = new HashSet<>(hospitalRepository.findAllById(dto.hospitalIds()));
        if (hospitals.size() != dto.hospitalIds().size()) {
            throw new EntityNotFoundException("One or more hospitals do not exist");
        }
        for (Hospital h : hospitals) {
            if (h.isDistributionCenter()) {
                throw new BusinessRuleException(h.getName() + " is a distribution center and cannot be supplied by another one");
            }
            hospitalRepository.findCenterOf(h.getId())
                    .filter(other -> !other.getId().equals(centerId))
                    .ifPresent(other -> {
                        throw new BusinessRuleException(h.getName() + " is already supplied by " + other.getName());
                    });
        }
        center.getCoveredHospitals().clear();
        center.getCoveredHospitals().addAll(hospitals);
        return HospitalDTO.of(hospitalRepository.save(center));
    }

    public Hospital findEntity(Long id) {
        return hospitalRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Hospital " + id + " not found"));
    }

    private void apply(Hospital h, HospitalFormDTO dto) {
        h.setName(dto.name().trim());
        h.setAcronym(dto.acronym() == null || dto.acronym().isBlank() ? null : dto.acronym().trim().toUpperCase());
        h.setPriceTableType(dto.priceTableType());
        if (dto.active() != null) h.setActive(dto.active());
        if (dto.productLines() != null) {
            h.getProductLines().clear();
            if (!dto.productLines().isEmpty()) h.getProductLines().addAll(EnumSet.copyOf(dto.productLines()));
        }
    }
}
