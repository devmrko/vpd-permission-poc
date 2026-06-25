package com.cloudhandson.vpdbackoffice.domain.vpd;

public record VpdPolicyView(
    String objectOwner,
    String objectName,
    String policyGroup,
    String policyName,
    String functionOwner,
    String packageName,
    String functionName,
    String statementTypes,
    String checkOption,
    String enabled,
    String staticPolicy,
    String policyType,
    String longPredicate
) {

  public String objectDisplayName() {
    return objectOwner + "." + objectName;
  }

  public String functionDisplayName() {
    String packagePrefix = packageName == null || packageName.isBlank() ? "" : packageName + ".";
    return functionOwner + "." + packagePrefix + functionName;
  }
}
