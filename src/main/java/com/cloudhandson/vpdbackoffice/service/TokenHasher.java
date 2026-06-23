package com.cloudhandson.vpdbackoffice.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class TokenHasher {

  public String sha256(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8))).toUpperCase();
    } catch (Exception e) {
      throw new AppException("Failed to hash bearer token");
    }
  }
}
