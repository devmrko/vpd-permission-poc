package com.cloudhandson.vpdbackoffice.domain.group;

public record GroupUserView(
    long groupId,
    String groupCode,
    String groupName,
    long userId,
    String username
) {
}
