package com.cloudhandson.vpdbackoffice.domain.vpd;

public record VpdPolicyDetail(
    VpdPolicyView policy,
    String ddl
) {
}
