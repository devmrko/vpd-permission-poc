package com.cloudhandson.vpdbackoffice.domain.vpd;

import java.util.List;

public record VpdPolicyFormOptions(
    List<String> policyNames,
    List<String> schemaOwners,
    List<String> owners,
    List<VpdFunctionOption> functions,
    List<VpdPolicyTemplateOption> policyTemplates,
    List<String> statementTypes
) {

  public String defaultPermissionFunctionKey() {
    return functions.stream()
        .filter(function -> "CB_AGENT_DOC_VPD_FILTER".equalsIgnoreCase(function.functionName()))
        .findFirst()
        .map(VpdFunctionOption::value)
        .orElse("");
  }
}
