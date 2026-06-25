package com.cloudhandson.vpdbackoffice.domain.vpd;

public record VpdTargetView(
    String owner,
    String objectName,
    String objectType,
    String protectedYn,
    String ordsPath,
    int policyCount,
    String policyNames,
    String filterNames
) {

  public String objectDisplayName() {
    return owner + "." + objectName;
  }

  public boolean protectedObject() {
    return "Y".equalsIgnoreCase(protectedYn);
  }

  public boolean vpdApplied() {
    return policyCount > 0;
  }
}
