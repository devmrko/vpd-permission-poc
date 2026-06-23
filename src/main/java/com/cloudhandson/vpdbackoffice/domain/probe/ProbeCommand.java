package com.cloudhandson.vpdbackoffice.domain.probe;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record ProbeCommand(
    @Positive long keyId,
    @Positive long objectId,
    @NotBlank String bearerToken,
    @Min(1) @Max(500) int limit
) {
}
