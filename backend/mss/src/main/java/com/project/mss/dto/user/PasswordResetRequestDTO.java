package com.project.mss.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PasswordResetRequestDTO(
        @NotBlank(message = "Email is required") @Email(message = "Invalid email") String email
) { }
