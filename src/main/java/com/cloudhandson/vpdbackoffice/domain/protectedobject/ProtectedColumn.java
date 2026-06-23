package com.cloudhandson.vpdbackoffice.domain.protectedobject;

public record ProtectedColumn(
    long columnId,
    long objectId,
    String columnName,
    String sensitiveYn,
    Long visibleRoleId
) {

  public boolean sensitive() {
    return "Y".equalsIgnoreCase(sensitiveYn);
  }
}
