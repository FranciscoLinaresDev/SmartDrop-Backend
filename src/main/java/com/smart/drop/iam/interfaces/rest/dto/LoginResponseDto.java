package com.smart.drop.iam.interfaces.rest.dto;

import java.time.LocalDateTime;

public record LoginResponseDto(
        Integer userId,
        String fullName,
        String email,
        LocalDateTime createdAt,
        String accessToken,
        String tokenType
) {
}