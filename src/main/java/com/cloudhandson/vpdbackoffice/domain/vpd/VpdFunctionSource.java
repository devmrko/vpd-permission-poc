package com.cloudhandson.vpdbackoffice.domain.vpd;

public record VpdFunctionSource(
    String owner,
    String objectName,
    String objectType,
    String source
) {

  public boolean found() {
    return source != null && !source.isBlank();
  }
}
