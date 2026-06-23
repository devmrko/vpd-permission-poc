package com.cloudhandson.vpdbackoffice.domain.protectedobject;

public record DatabaseObjectOption(
    String owner,
    String objectName,
    String objectType
) {

  public String value() {
    return owner + "." + objectName;
  }

  public String label() {
    return owner + "." + objectName + " (" + objectType + ")";
  }
}
