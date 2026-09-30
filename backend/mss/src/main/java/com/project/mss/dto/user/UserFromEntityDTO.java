package com.project.mss.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserFromEntityDTO(
        String username,
        @NotBlank
        String name,
        @NotBlank
        @Email
        String email
) {}