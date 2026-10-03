package com.project.mss.dto.user;

import java.util.Set;

import jakarta.validation.constraints.NotNull;

/** Hospitals the user works at. An empty set removes every hospital. */
public record UserHospitalsDTO(@NotNull Set<Long> hospitalIds) { }
