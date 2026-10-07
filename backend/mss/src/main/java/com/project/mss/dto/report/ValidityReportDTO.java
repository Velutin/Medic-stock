package com.project.mss.dto.report;

import java.time.LocalDate;

import com.project.mss.model.enums.Location;

/** Lot expired or expiring within the window, with where it is. daysLeft is negative when already expired. */
public record ValidityReportDTO(String hospital, Location location, String ref, String description, String lot,
                                LocalDate expiryDate, long daysLeft, int quantity) { }
