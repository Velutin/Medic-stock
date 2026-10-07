package com.project.mss.dto.user;

import com.project.mss.util.validation.StrongPassword;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordDTO(
        @NotBlank(message = "Current password is required") String currentPassword,
        @StrongPassword String newPassword
) { }
