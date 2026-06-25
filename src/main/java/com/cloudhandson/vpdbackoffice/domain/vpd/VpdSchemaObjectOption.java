package com.cloudhandson.vpdbackoffice.domain.vpd;

public record VpdSchemaObjectOption(
    String owner,
    String objectName,
    String objectType
) {
}
