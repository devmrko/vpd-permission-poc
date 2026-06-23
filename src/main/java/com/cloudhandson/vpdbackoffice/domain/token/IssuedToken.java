package com.cloudhandson.vpdbackoffice.domain.token;

import java.time.OffsetDateTime;

public record IssuedToken(
    long keyId,
    String prefix,
    String plainToken,
    OffsetDateTime expiresAt
) {
}
