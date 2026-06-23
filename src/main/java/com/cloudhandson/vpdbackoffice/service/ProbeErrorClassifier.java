package com.cloudhandson.vpdbackoffice.service;

import com.cloudhandson.vpdbackoffice.domain.probe.ProbeStatus;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
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
    return hasCause(exception, SocketTimeoutException.class);
  }

  public boolean isUnavailable(ResourceAccessException exception) {
    return hasCause(exception, ConnectException.class)
        || hasCause(exception, NoRouteToHostException.class)
        || hasCause(exception, UnknownHostException.class);
  }

  private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
    Throwable cause = throwable;
    while (cause != null) {
      if (type.isInstance(cause)) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }
}
