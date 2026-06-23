package com.cloudhandson.vpdbackoffice.service;

import com.cloudhandson.vpdbackoffice.config.BackofficeProperties;
import com.cloudhandson.vpdbackoffice.domain.audit.AuditEvent;
import com.cloudhandson.vpdbackoffice.domain.token.BearerTokenRecord;
import com.cloudhandson.vpdbackoffice.domain.token.IssuedToken;
import com.cloudhandson.vpdbackoffice.domain.token.TokenIssueCommand;
import com.cloudhandson.vpdbackoffice.domain.user.AppUser;
import com.cloudhandson.vpdbackoffice.mapper.BearerTokenMapper;
import com.cloudhandson.vpdbackoffice.mapper.UserMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BearerTokenService {

  private final BearerTokenMapper tokenMapper;
  private final UserMapper userMapper;
  private final AuditService auditService;
  private final TokenGenerator tokenGenerator;
  private final TokenHasher tokenHasher;
  private final BackofficeProperties properties;
  private final Clock clock;

  public BearerTokenService(
      BearerTokenMapper tokenMapper,
      UserMapper userMapper,
      AuditService auditService,
      TokenGenerator tokenGenerator,
      TokenHasher tokenHasher,
      BackofficeProperties properties,
      Clock clock
  ) {
    this.tokenMapper = tokenMapper;
    this.userMapper = userMapper;
    this.auditService = auditService;
    this.tokenGenerator = tokenGenerator;
    this.tokenHasher = tokenHasher;
    this.properties = properties;
    this.clock = clock;
  }

  public List<BearerTokenRecord> findAll() {
    return tokenMapper.findAll();
  }

  public BearerTokenRecord findById(long keyId) {
    return tokenMapper.findById(keyId);
  }

  public boolean matches(BearerTokenRecord record, String plainToken) {
    if (record == null || plainToken == null || plainToken.isBlank()) {
      return false;
    }
    String hash = tokenHasher.sha256(plainToken);
    return hash.equalsIgnoreCase(record.keyHash());
  }

  @Transactional
  public IssuedToken issueToken(TokenIssueCommand command) {
    AppUser user = userMapper.findById(command.userId());
    if (user == null) {
      throw new AppException("사용자를 찾을 수 없습니다.");
    }
    if (!user.active()) {
      throw new AppException("비활성 사용자에게는 토큰을 발급할 수 없습니다.");
    }

    OffsetDateTime now = OffsetDateTime.now(clock);
    if (command.expiresAt() == null || !command.expiresAt().isAfter(now)) {
      throw new AppException("만료일은 현재 시각 이후여야 합니다.");
    }
    if (command.expiresAt().isAfter(now.plusDays(properties.token().maxDays()))) {
      throw new AppException("토큰 만료일이 최대 허용 기간을 초과했습니다.");
    }

    String plainToken = tokenGenerator.generate();
    String prefix = tokenGenerator.prefix(plainToken);
    String hash = tokenHasher.sha256(plainToken);
    long keyId = tokenMapper.nextKeyId();

    tokenMapper.insertToken(new BearerTokenRecord(
        keyId,
        user.userId(),
        user.username(),
        prefix,
        hash,
        command.expiresAt(),
        null,
        command.description()
    ));
    auditService.record(new AuditEvent("TOKEN_ISSUED", keyId, null, "SUCCESS", null, null, "issued"));
    return new IssuedToken(keyId, prefix, plainToken, command.expiresAt());
  }

  @Transactional
  public void revokeToken(long keyId, String reason) {
    int updated = tokenMapper.revokeToken(keyId, OffsetDateTime.now(clock), reason);
    if (updated == 0) {
      throw new AppException("회수할 활성 토큰을 찾을 수 없습니다.");
    }
    auditService.record(new AuditEvent("TOKEN_REVOKED", keyId, null, "SUCCESS", null, null, reason));
  }
}
