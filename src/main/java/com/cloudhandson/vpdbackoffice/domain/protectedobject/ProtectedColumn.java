package com.cloudhandson.vpdbackoffice.domain.protectedobject;

public record ProtectedColumn(
    long columnId,
    long objectId,
    String columnName,
    String sensitiveYn,
    Long visibleRoleId,
    String sensitivityLevel,
    String redactionMethod
) {

  public boolean sensitive() {
    return "Y".equalsIgnoreCase(sensitiveYn)
        || !"PUBLIC".equalsIgnoreCase(sensitivityLevel);
  }

  public String policyLabel() {
    String level = sensitivityLevel == null || sensitivityLevel.isBlank() ? "PUBLIC" : sensitivityLevel;
    String method = redactionMethod == null || redactionMethod.isBlank() ? "NONE" : redactionMethod;
    return level + "/" + method;
  }
}
