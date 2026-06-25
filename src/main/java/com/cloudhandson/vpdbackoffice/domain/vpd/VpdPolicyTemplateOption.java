package com.cloudhandson.vpdbackoffice.domain.vpd;

public record VpdPolicyTemplateOption(
    String policyName,
    String functionOwner,
    String packageName,
    String functionName,
    String statementTypes,
    String enabled,
    String checkOption
) {

  public String functionKey() {
    String packagePrefix = packageName == null || packageName.isBlank() ? "" : packageName + ".";
    return functionOwner + "." + packagePrefix + functionName;
  }

  public String filterDisplayName() {
    return functionKey();
  }

  public String label() {
    return policyName + " / " + filterDisplayName() + " / " + statementTypes;
  }

  public boolean enabledValue() {
    return "YES".equalsIgnoreCase(enabled);
  }

  public boolean updateCheckValue() {
    return "YES".equalsIgnoreCase(checkOption);
  }
}
