package com.project.mss.dto.user;

import java.util.List;

public record AuthResponseDTO(
    String token,
    List<String> roles
) { }
