package com.project.mss.dto.hospital;

import java.util.Set;

import jakarta.validation.constraints.NotNull;

/** Hospitals supplied by a distribution center. An empty set removes every hospital. */
public record CoveredHospitalsDTO(@NotNull Set<Long> hospitalIds) { }
