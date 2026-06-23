package com.cloudhandson.vpdbackoffice.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenHasherTest {

  private final TokenHasher tokenHasher = new TokenHasher();

  @Test
  void sha256MatchesOracleStandardHashSeedValue() {
    assertThat(tokenHasher.sha256("cb_hr_key"))
        .isEqualTo("240CD9BCE11219EAFB691226D5868C2BE4D1600F6460C77C0485CB38D56FC053");
  }
}
