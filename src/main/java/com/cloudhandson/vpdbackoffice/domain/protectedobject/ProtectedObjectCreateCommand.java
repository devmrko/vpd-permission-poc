package com.cloudhandson.vpdbackoffice.domain.protectedobject;

import jakarta.validation.constraints.NotBlank;

public record ProtectedObjectCreateCommand(
    @NotBlank String owner,
    @NotBlank String objectName,
    @NotBlank String ordsPath,
    String columns,
    String sensitiveColumns
) {
}
