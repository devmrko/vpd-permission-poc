package com.cloudhandson.vpdbackoffice.domain.vpd;

public record VpdPolicyCreateCommand(
    String objectOwner,
    String objectName,
    String policyName,
    String functionKey,
    String functionOwner,
    String functionName,
    String statementTypes,
    boolean enabled,
    boolean updateCheck,
    String filterPredicate
) {
}
