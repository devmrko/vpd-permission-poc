package com.cloudhandson.vpdbackoffice.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenGeneratorTest {

  private final TokenGenerator tokenGenerator = new TokenGenerator();

  @Test
  void generateCreatesVpdLiveTokenAndSixteenCharacterPrefix() {
    String token = tokenGenerator.generate();

    assertThat(token).startsWith("vpd_live_");
    assertThat(token).hasSizeGreaterThan(40);
    assertThat(tokenGenerator.prefix(token)).hasSize(16);
  }
}
