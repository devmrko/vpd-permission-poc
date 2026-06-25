package com.cloudhandson.vpdbackoffice.domain.permission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record PermissionSetCommand(
    @Positive long roleId,
    @Positive long objectId,
    @NotBlank String action,
    String permissionEffect,
    @NotEmpty List<RuleCommand> rules,
    List<String> visibleColumns
) {
}
