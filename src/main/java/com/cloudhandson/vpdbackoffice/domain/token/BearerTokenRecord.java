package com.cloudhandson.vpdbackoffice.domain.token;

import java.time.LocalDateTime;

public record BearerTokenRecord(
    long keyId,
    long userId,
    String username,
    String keyPrefix,
    String keyHash,
    LocalDateTime expiresAt,
    LocalDateTime revokedAt,
    String description
) {

  public boolean active(LocalDateTime now) {
    return revokedAt == null && expiresAt != null && expiresAt.isAfter(now);
  }
}
