package com.project.mss.dto.user;

import java.util.Set;

import com.project.mss.model.enums.UserRole;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * New user. No password: an invitation e-mail is sent so the user creates it on first access.
 * cpf and phone accept masks ("123.456.789-09", "(71) 99999-9999"); only the digits are stored.
 * role: ADMIN (administrator), SURGICAL_TECH (surgical tech) or USER (read-only).
 */
public record UserFormDTO(
        @NotBlank(message = "Name is required") @Size(max = 255) String name,
        @NotBlank(message = "Email is required") @Email(message = "Invalid email") String email,
        @NotBlank(message = "CPF is required") String cpf,
        @NotBlank(message = "Mobile phone is required") String phone,
        @NotNull(message = "Role is required") UserRole role,
        Set<Long> hospitalIds
) { }
