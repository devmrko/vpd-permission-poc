package com.cloudhandson.vpdbackoffice.domain.protectedobject;

public record ProtectedObject(
    long objectId,
    String owner,
    String objectName,
    String ordsPath,
    String enabledYn
) {

  public boolean enabled() {
    return "Y".equalsIgnoreCase(enabledYn);
  }

  public String displayName() {
    return owner + "." + objectName;
  }
}
