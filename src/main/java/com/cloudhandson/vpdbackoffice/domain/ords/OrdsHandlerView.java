package com.cloudhandson.vpdbackoffice.domain.ords;

public record OrdsHandlerView(
    String schemaName,
    String moduleName,
    String basePath,
    String template,
    String method,
    String sourceType,
    String source,
    String parameters,
    String packageSource
) {

  public String fullPath() {
    return "/ords/" + schemaName + "/" + basePath + template;
  }
}
