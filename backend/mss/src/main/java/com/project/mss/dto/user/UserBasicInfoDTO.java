package com.project.mss.dto.user;

import jakarta.validation.constraints.NotBlank;

public record UserBasicInfoDTO(
        @NotBlank
        String username) {
}
