package com.cloudhandson.vpdbackoffice.domain.schema;

public record SchemaActionResult(
    String objectName,
    String action,
    String status,
    String message
) {
}
