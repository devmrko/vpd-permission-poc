package com.cloudhandson.vpdbackoffice.domain.user;

public record UserRoleView(
    long userId,
    String username,
    long roleId,
    String roleName
) {
}
