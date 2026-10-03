package com.project.mss.dto.surgery;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/** Patient name and date are typed manually. */
public record SurgeryFormDTO(
        @NotNull Long hospitalId,
        @NotBlank(message = "Patient name is required") @Size(max = 200) String patientName,
        @NotNull @PastOrPresent LocalDate surgeryDate,
        @Size(max = 200) String doctor,
        Long surgicalTechId,          // optional; administrators may record a surgery for another surgical tech
        String notes
) { }
