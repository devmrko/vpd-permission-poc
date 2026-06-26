package com.cloudhandson.vpdbackoffice.domain.group;

public record GroupRoleView(
    long groupId,
    String groupCode,
    String groupName,
    long roleId,
    String roleName
) {
}
