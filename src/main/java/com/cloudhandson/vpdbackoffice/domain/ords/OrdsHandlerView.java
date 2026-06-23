package com.cloudhandson.vpdbackoffice.domain.ords;

import java.util.Locale;

public record OrdsHandlerView(
    Long handlerId,
    String schemaName,
    String parsingSchema,
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

  public boolean plsqlEditable() {
    return sourceType != null && sourceType.toLowerCase(Locale.ROOT).contains("plsql");
  }
}
