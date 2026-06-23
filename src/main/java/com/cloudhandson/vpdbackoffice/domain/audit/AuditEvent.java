package com.cloudhandson.vpdbackoffice.domain.audit;

public record AuditEvent(
    String eventType,
    Long keyId,
    Long objectId,
    String status,
    Integer rowCount,
    String errorCode,
    String message
) {
}
