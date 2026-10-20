package com.smart.drop.iam.interfaces.rest.dto;

public record VerifyTwoFactorChallengeRequestDto(
        String challengeId,
        String code
) {
}
