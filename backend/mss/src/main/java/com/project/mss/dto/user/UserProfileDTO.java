package com.project.mss.dto.user;

import java.util.List;

public record UserProfileDTO(
        Long userId,        
        String username,
        String email,
        List<String> roles
) { }
