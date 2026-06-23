package com.cloudhandson.vpdbackoffice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudhandson.vpdbackoffice.domain.probe.ProbeStatus;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;

class ProbeErrorClassifierTest {

  private final ProbeErrorClassifier classifier = new ProbeErrorClassifier();

  @Test
  void classifiesInvalidBearerKey() {
    assertThat(classifier.classify(HttpStatus.FORBIDDEN, "{\"error\":\"ORA-20002\"}"))
        .isEqualTo(ProbeStatus.INVALID_TOKEN);
  }

  @Test
  void classifiesObjectAccessFailure() {
    assertThat(classifier.classify(HttpStatus.FORBIDDEN, "ORA-00942: table or view does not exist"))
        .isEqualTo(ProbeStatus.OBJECT_NOT_ACCESSIBLE);
  }

  @Test
  void classifiesHttpUnauthorizedAsInvalidToken() {
    assertThat(classifier.classify(HttpStatus.UNAUTHORIZED, "unauthorized"))
        .isEqualTo(ProbeStatus.INVALID_TOKEN);
  }

  @Test
  void classifiesOrdsNotFound() {
    assertThat(classifier.classify(HttpStatus.NOT_FOUND, "{\"code\":\"NotFound\"}"))
        .isEqualTo(ProbeStatus.ORDS_PATH_NOT_FOUND);
  }

  @Test
  void detectsOrdsConnectionRefused() {
    assertThat(classifier.isUnavailable(new ResourceAccessException(
        "I/O error",
        new ConnectException("Connection refused")
    ))).isTrue();
  }

  @Test
  void detectsOrdsTimeout() {
    assertThat(classifier.isTimeout(new ResourceAccessException(
        "I/O error",
        new SocketTimeoutException("Read timed out")
    ))).isTrue();
  }
}
