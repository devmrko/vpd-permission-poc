package com.cloudhandson.vpdbackoffice.domain.vpd;

public record VpdFunctionOption(
    String owner,
    String packageName,
    String functionName,
    String objectType
) {

  public String value() {
    String packagePrefix = packageName == null || packageName.isBlank() ? "" : packageName + ".";
    return owner + "." + packagePrefix + functionName;
  }

  public String label() {
    return value() + " / " + objectType;
  }
}
