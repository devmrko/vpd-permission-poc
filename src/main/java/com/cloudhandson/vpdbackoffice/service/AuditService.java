package com.cloudhandson.vpdbackoffice.service;

import com.cloudhandson.vpdbackoffice.domain.audit.AuditEvent;
import com.cloudhandson.vpdbackoffice.mapper.AuditMapper;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

  private final AuditMapper auditMapper;

  public AuditService(AuditMapper auditMapper) {
    this.auditMapper = auditMapper;
  }

  public void record(AuditEvent event) {
    auditMapper.insert(event);
  }
}
