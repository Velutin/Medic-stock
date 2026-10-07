package com.project.mss.dto.user;

import com.project.mss.util.validation.StrongPassword;

import jakarta.validation.constraints.NotBlank;

/** Creates the password from the e-mail link: first access (invitation) or password reset. */
public record PasswordResetDTO(
        @NotBlank(message = "Token is required") String token,
        @StrongPassword String newPassword
) { }
