package com.cloudhandson.vpdbackoffice.domain.permission;

import jakarta.validation.constraints.NotBlank;

public record RuleCommand(
    String ruleColumn,
    @NotBlank String ruleType,
    String ruleValue
) {
}
