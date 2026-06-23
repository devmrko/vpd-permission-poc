package com.cloudhandson.vpdbackoffice.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "backoffice")
public record BackofficeProperties(
    Security security,
    Token token,
    Ords ords
) {

  public record Security(String adminUser, String adminPassword) {
  }

  public record Token(int maxDays) {
  }

  public record Ords(String baseUrl, Duration timeout) {
  }
}
