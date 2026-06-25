package com.cloudhandson.vpdbackoffice.domain.vpd;

public record VpdPolicyExplanation(
    String status,
    String answer,
    String prompt,
    VpdPolicyDetail detail,
    VpdFunctionSource functionSource
) {
}
