package com.cloudhandson.vpdbackoffice.service;

import com.cloudhandson.vpdbackoffice.domain.probe.ProbeStatus;
import java.net.SocketTimeoutException;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

@Component
public class ProbeErrorClassifier {

  public ProbeStatus classify(HttpStatusCode status, String body) {
    String text = body == null ? "" : body;
    if (text.contains("ORA-20002")) {
      return ProbeStatus.INVALID_TOKEN;
    }
    if (text.contains("ORA-00942") || text.contains("ORA-01031")) {
      return ProbeStatus.OBJECT_NOT_ACCESSIBLE;
    }
    if (status != null && (status.value() == 401 || status.value() == 403)) {
      return ProbeStatus.INVALID_TOKEN;
    }
    return ProbeStatus.UNKNOWN_ERROR;
  }

  public boolean isTimeout(ResourceAccessException exception) {
    Throwable cause = exception;
    while (cause != null) {
      if (cause instanceof SocketTimeoutException) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }
}
