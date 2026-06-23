package com.cloudhandson.vpdbackoffice.domain.permission;

public record PermissionRule(
    long ruleId,
    long permissionId,
    String ruleColumn,
    String ruleType,
    String ruleValue
) {
}
