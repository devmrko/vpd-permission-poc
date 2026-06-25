package com.cloudhandson.vpdbackoffice.domain.vpd;

import java.util.List;

public record VpdPolicyFormOptions(
    List<String> policyNames,
    List<String> owners,
    List<VpdFunctionOption> functions,
    List<String> statementTypes
) {
}
