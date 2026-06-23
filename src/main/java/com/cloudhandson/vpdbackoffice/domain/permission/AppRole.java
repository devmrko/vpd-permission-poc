package com.cloudhandson.vpdbackoffice.domain.permission;

public record AppRole(
    long roleId,
    String roleName,
    String description
) {
}
