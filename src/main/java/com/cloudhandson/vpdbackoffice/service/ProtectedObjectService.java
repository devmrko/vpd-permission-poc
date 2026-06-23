package com.cloudhandson.vpdbackoffice.service;

import com.cloudhandson.vpdbackoffice.domain.audit.AuditEvent;
import com.cloudhandson.vpdbackoffice.domain.protectedobject.ProtectedColumn;
import com.cloudhandson.vpdbackoffice.domain.protectedobject.ProtectedObject;
import com.cloudhandson.vpdbackoffice.domain.protectedobject.ProtectedObjectCreateCommand;
import com.cloudhandson.vpdbackoffice.mapper.ProtectedObjectMapper;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProtectedObjectService {

  private final ProtectedObjectMapper mapper;
  private final AuditService auditService;

  public ProtectedObjectService(ProtectedObjectMapper mapper, AuditService auditService) {
    this.mapper = mapper;
    this.auditService = auditService;
  }

  public List<ProtectedObject> findEnabled() {
    return mapper.findEnabled();
  }

  public ProtectedObject assertEnabled(long objectId) {
    ProtectedObject object = mapper.findById(objectId);
    if (object == null || !object.enabled()) {
      throw new AppException("활성 보호 객체를 찾을 수 없습니다.");
    }
    return object;
  }

  public List<ProtectedColumn> findColumns(long objectId) {
    return mapper.findColumns(objectId);
  }

  @Transactional
  public void createObject(ProtectedObjectCreateCommand command) {
    long objectId = mapper.nextObjectId();
    mapper.insertObject(objectId, command);
    Set<String> sensitive = splitCsv(command.sensitiveColumns());
    for (String column : splitCsv(command.columns())) {
      mapper.insertColumn(mapper.nextColumnId(), objectId, column, sensitive.contains(column) ? "Y" : "N");
    }
    auditService.record(new AuditEvent("PROTECTED_OBJECT_CREATED", null, objectId, "SUCCESS", null, null,
        command.objectName()));
  }

  @Transactional
  public void disableObject(long objectId) {
    int updated = mapper.disableObject(objectId);
    if (updated == 0) {
      throw new AppException("보호 객체를 찾을 수 없습니다.");
    }
    auditService.record(new AuditEvent("PROTECTED_OBJECT_DISABLED", null, objectId, "SUCCESS", null, null, null));
  }

  private Set<String> splitCsv(String value) {
    Set<String> result = new HashSet<>();
    if (value == null || value.isBlank()) {
      return result;
    }
    Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(token -> !token.isBlank())
        .map(token -> token.toUpperCase(Locale.ROOT))
        .forEach(result::add);
    return result;
  }
}
