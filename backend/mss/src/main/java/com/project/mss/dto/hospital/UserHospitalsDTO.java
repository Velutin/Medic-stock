package com.project.mss.dto.hospital;

import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Sets which hospitals a user (surgical tech) works at. */
public record UserHospitalsDTO(
        @NotBlank String username,
        @NotNull Set<Long> hospitalIds
) { }
