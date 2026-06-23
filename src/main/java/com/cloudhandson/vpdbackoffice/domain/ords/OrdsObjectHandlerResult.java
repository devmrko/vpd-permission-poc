package com.cloudhandson.vpdbackoffice.domain.ords;

public record OrdsObjectHandlerResult(
    long objectId,
    String ordsPath,
    String moduleName,
    String template
) {
}
