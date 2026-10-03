package com.project.mss.dto.user;

import com.project.mss.model.enums.UserRole;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** Partial update (PATCH): only the informed fields change. active=false blocks access immediately. */
public record UserUpdateDTO(
        @Size(max = 255) String name,
        @Email(message = "Invalid email") String email,
        String cpf,
        String phone,
        UserRole role,
        Boolean active
) { }
