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
    String errorMessage,
    String requestHeaders,
    String requestPayload,
    String responseHeaders,
    String responseBody
) {

  public static ProbeResult blocked(ProbeStatus status, String errorCode, String errorMessage) {
    return blocked(status, errorCode, errorMessage, null, null, null, null);
  }

  public static ProbeResult blocked(
      ProbeStatus status,
      String errorCode,
      String errorMessage,
      String requestHeaders,
      String requestPayload,
      String responseHeaders,
      String responseBody
  ) {
    return new ProbeResult(
        status,
        List.of(),
        List.of(),
        0,
        List.of(),
        errorCode,
        errorMessage,
        requestHeaders,
        requestPayload,
        responseHeaders,
        responseBody
    );
  }
}
