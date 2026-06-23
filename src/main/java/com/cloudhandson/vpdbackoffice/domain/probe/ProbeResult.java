package com.cloudhandson.vpdbackoffice.domain.probe;

import java.util.List;
import java.util.Map;

public record ProbeResult(
    ProbeStatus status,
    List<String> columns,
    List<Map<String, Object>> rows,
    int rowCount,
    List<String> maskedColumns,
    String errorCode,
    String errorMessage
) {

  public static ProbeResult blocked(ProbeStatus status, String errorCode, String errorMessage) {
    return new ProbeResult(status, List.of(), List.of(), 0, List.of(), errorCode, errorMessage);
  }
}
