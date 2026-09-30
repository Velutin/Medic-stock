package com.project.mss.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleDTO(
        @NotBlank
        String username,
        @NotNull
        String role
) { }
