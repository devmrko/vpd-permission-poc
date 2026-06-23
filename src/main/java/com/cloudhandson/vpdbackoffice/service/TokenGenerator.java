package com.cloudhandson.vpdbackoffice.service;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class TokenGenerator {

  private final SecureRandom secureRandom = new SecureRandom();

  public String generate() {
    byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    return "vpd_live_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public String prefix(String token) {
    if (token == null || token.length() <= 16) {
      return token;
    }
    return token.substring(0, 16);
  }
}
