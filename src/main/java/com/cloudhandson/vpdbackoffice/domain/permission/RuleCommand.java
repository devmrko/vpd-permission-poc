package com.cloudhandson.vpdbackoffice.domain.permission;

import jakarta.validation.constraints.NotBlank;

public record RuleCommand(
    @NotBlank String ruleType,
    String ruleValue
) {
}
