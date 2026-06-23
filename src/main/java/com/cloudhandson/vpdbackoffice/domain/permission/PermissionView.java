package com.cloudhandson.vpdbackoffice.domain.permission;

public record PermissionView(
    long permissionId,
    long roleId,
    String roleName,
    long objectId,
    String objectName,
    String action,
    String rules,
    String visibleColumns,
    String filterPreview,
    String nullPolicyPreview
) {
}
