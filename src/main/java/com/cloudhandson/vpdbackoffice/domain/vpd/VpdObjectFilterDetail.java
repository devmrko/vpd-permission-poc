package com.cloudhandson.vpdbackoffice.domain.vpd;

import java.util.List;

public record VpdObjectFilterDetail(
    String objectOwner,
    String objectName,
    List<Row> rows
) {

  public String objectDisplayName() {
    return objectOwner + "." + objectName;
  }

  public record Row(
      VpdPolicyView policy,
      VpdFunctionSource source
  ) {
  }
}
