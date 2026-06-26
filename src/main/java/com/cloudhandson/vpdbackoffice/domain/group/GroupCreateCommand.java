package com.cloudhandson.vpdbackoffice.domain.group;

public record GroupCreateCommand(
    String groupCode,
    String groupName,
    String description
) {
}
