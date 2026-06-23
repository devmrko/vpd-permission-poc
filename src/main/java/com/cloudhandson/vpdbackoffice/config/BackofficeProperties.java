package com.cloudhandson.vpdbackoffice.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "backoffice")
public record BackofficeProperties(
    Security security,
    Token token,
    Ords ords,
    Ai ai
) {

  public record Security(String adminUser, String adminPassword) {
  }

  public record Token(int maxDays) {
  }

  public record Ords(String baseUrl, Duration timeout) {
  }

  public record Ai(boolean enabled, String baseUrl, String model, String apiKey, Duration timeout) {
  }
}
