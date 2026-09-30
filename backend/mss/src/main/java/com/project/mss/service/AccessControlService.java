package com.project.mss.service;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.project.mss.exception.EntityNotFoundException;
import com.project.mss.model.entity.Hospital;
import com.project.mss.model.entity.User;
import com.project.mss.repository.HospitalRepository;
import com.project.mss.repository.UserRepository;

/**
 * Visibility rules: ADMIN/MASTER see every hospital;
 * other users (surgical techs) only see the hospitals they work at.
 */
@Service
public class AccessControlService {

    private final UserRepository userRepository;
    private final HospitalRepository hospitalRepository;

    public AccessControlService(UserRepository userRepository, HospitalRepository hospitalRepository) {
        this.userRepository = userRepository;
        this.hospitalRepository = hospitalRepository;
    }

    public User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User principal)) {
            throw new AccessDeniedException("User not authenticated");
        }
        // Reload to get an active JPA session and up-to-date hospitals
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new AccessDeniedException("User not found"));
    }

    public Set<Long> allowedHospitals() {
        User user = currentUser();
        if (user.isManager()) {
            return hospitalRepository.findAll().stream().map(Hospital::getId).collect(Collectors.toSet());
        }
        return user.getHospitals().stream().map(Hospital::getId).collect(Collectors.toSet());
    }

    /** Loads the hospital and ensures the current user can access it. */
    public Hospital requireHospitalAccess(Long hospitalId) {
        Hospital hospital = hospitalRepository.findById(hospitalId)
                .orElseThrow(() -> new EntityNotFoundException("Hospital " + hospitalId + " not found"));
        User user = currentUser();
        if (!user.isManager() && user.getHospitals().stream().noneMatch(h -> h.getId().equals(hospitalId))) {
            throw new AccessDeniedException("You do not have access to hospital " + hospital.getName());
        }
        return hospital;
    }

    public void requireManager() {
        if (!currentUser().isManager()) {
            throw new AccessDeniedException("Operation restricted to administrators");
        }
    }
}
