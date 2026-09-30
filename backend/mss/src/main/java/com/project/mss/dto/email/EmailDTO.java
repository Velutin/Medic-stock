package com.project.mss.dto.email;

public record EmailDTO(
        Long userId,
        String emailTo,
        String subject,
        String body
) { }
