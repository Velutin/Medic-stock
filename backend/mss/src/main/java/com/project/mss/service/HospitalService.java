package com.project.mss.service;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.mss.dto.hospital.HospitalDTO;
import com.project.mss.dto.hospital.HospitalFormDTO;
import com.project.mss.dto.hospital.UserHospitalsDTO;
import com.project.mss.exception.BusinessRuleException;
import com.project.mss.exception.EntityNotFoundException;
import com.project.mss.model.entity.Hospital;
import com.project.mss.model.entity.User;
import com.project.mss.repository.HospitalRepository;
import com.project.mss.repository.UserRepository;

@Service
public class HospitalService {

    private final HospitalRepository hospitalRepository;
    private final UserRepository userRepository;
    private final AccessControlService accessControlService;

    public HospitalService(HospitalRepository hospitalRepository, UserRepository userRepository,
                           AccessControlService accessControlService) {
        this.hospitalRepository = hospitalRepository;
        this.userRepository = userRepository;
        this.accessControlService = accessControlService;
    }

    @Transactional(readOnly = true)
    public List<HospitalDTO> listVisible() {
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
        apply(h, dto);
        return HospitalDTO.of(hospitalRepository.save(h));
    }

    @Transactional
    public HospitalDTO update(Long id, HospitalFormDTO dto) {
        Hospital h = findEntity(id);
        apply(h, dto);
        return HospitalDTO.of(hospitalRepository.save(h));
    }

    @Transactional
    public void setUserHospitals(UserHospitalsDTO dto) {
        User user = (User) userRepository.findByUsername(dto.username());
        if (user == null) {
            throw new EntityNotFoundException("User " + dto.username() + " not found");
        }
        var hospitals = new HashSet<>(hospitalRepository.findAllById(dto.hospitalIds()));
        if (hospitals.size() != dto.hospitalIds().size()) {
            throw new EntityNotFoundException("One or more hospitals do not exist");
        }
        user.getHospitals().clear();
        user.getHospitals().addAll(hospitals);
        userRepository.save(user);
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
