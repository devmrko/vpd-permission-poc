package com.cloudhandson.vpdbackoffice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudhandson.vpdbackoffice.domain.probe.ProbeStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

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
}
