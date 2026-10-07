package com.project.mss.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** rememberMe: keeps the session for 7 days on this device; otherwise it ends after 30 minutes without use. */
public record LoginDTO(
        @NotBlank(message = "Email is required") @Email(message = "Invalid email") String email,
        @NotBlank(message = "Password is required") String password,
        boolean rememberMe
) { }
