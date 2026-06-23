package com.cloudhandson.vpdbackoffice.domain.user;

import jakarta.validation.constraints.NotBlank;

public record UserCreateCommand(
    @NotBlank String username,
    @NotBlank String empNo,
    @NotBlank String deptCode
) {
}
