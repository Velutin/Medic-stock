package com.project.mss.dto.user;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import com.project.mss.model.entity.Hospital;
import com.project.mss.model.entity.User;
import com.project.mss.model.enums.UserRole;

/**
 * role: the user's main profile (MASTER, ADMIN, SURGICAL_TECH or USER).
 * status: ACTIVE, PENDING_FIRST_ACCESS (invitation not used yet) or INACTIVE.
 */
public record UserDTO(Long id, String name, String email, String cpf, String phone, UserRole role,
                      String status, List<HospitalRef> hospitals, LocalDateTime createdAt) {

    public record HospitalRef(Long id, String name) { }

    public static UserDTO of(User u) {
        String status = !Boolean.TRUE.equals(u.getIsActive()) ? "INACTIVE"
                : u.isPendingFirstAccess() ? "PENDING_FIRST_ACCESS" : "ACTIVE";
        return new UserDTO(u.getId(), u.getName(), u.getEmail(), u.getCpf(), u.getPhone(), mainRole(u), status,
                u.getHospitals().stream().sorted(Comparator.comparing(Hospital::getName))
                        .map(h -> new HospitalRef(h.getId(), h.getName())).toList(),
                u.getCreatedAt());
    }

    /** Highest profile among the user's roles. */
    public static UserRole mainRole(User u) {
        for (UserRole r : List.of(UserRole.MASTER, UserRole.ADMIN, UserRole.SURGICAL_TECH)) {
            if (u.hasAnyRole(r.name())) return r;
        }
        return UserRole.USER;
    }
}
