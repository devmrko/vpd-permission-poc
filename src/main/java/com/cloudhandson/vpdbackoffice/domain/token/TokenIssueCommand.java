package com.cloudhandson.vpdbackoffice.domain.token;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record TokenIssueCommand(
    @Positive long userId,
    @Future OffsetDateTime expiresAt,
    @Size(max = 200) String description
) {
}
