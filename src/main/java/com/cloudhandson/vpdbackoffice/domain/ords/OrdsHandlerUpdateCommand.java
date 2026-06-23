package com.cloudhandson.vpdbackoffice.domain.ords;

import jakarta.validation.constraints.NotBlank;

public record OrdsHandlerUpdateCommand(
    long handlerId,
    @NotBlank String source
) {
}
