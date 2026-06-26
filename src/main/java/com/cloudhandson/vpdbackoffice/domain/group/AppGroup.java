package com.cloudhandson.vpdbackoffice.domain.group;

public record AppGroup(
    long groupId,
    String groupCode,
    String groupName,
    String description,
    String activeYn
) {

  public boolean active() {
    return "Y".equalsIgnoreCase(activeYn);
  }
}
