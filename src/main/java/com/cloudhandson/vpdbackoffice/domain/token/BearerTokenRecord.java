package com.cloudhandson.vpdbackoffice.domain.token;

import java.time.OffsetDateTime;

public record BearerTokenRecord(
    long keyId,
    long userId,
    String username,
    String keyPrefix,
    String keyHash,
    OffsetDateTime expiresAt,
    OffsetDateTime revokedAt,
    String description
) {

  public boolean active(OffsetDateTime now) {
    return revokedAt == null && expiresAt != null && expiresAt.isAfter(now);
  }
}
