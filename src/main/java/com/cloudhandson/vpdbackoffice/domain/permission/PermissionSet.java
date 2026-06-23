package com.cloudhandson.vpdbackoffice.domain.permission;

import java.util.List;

public record PermissionSet(
    long permissionId,
    long roleId,
    long objectId,
    String action,
    List<PermissionRule> rules,
    List<String> visibleColumns
) {
}
