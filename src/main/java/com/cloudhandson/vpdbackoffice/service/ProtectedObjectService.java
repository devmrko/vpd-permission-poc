package com.cloudhandson.vpdbackoffice.service;

import com.cloudhandson.vpdbackoffice.domain.audit.AuditEvent;
import com.cloudhandson.vpdbackoffice.domain.protectedobject.DatabaseObjectOption;
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

  public List<DatabaseObjectOption> findDatabaseObjects() {
    return mapper.findDatabaseObjects();
  }

  public List<String> findDatabaseColumns(String owner, String objectName) {
    return mapper.findDatabaseColumns(owner, objectName);
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
    ProtectedObjectCreateCommand normalized = normalizeCreateCommand(command);
    long objectId = mapper.nextObjectId();
    mapper.insertObject(objectId, normalized);
    Set<String> sensitive = splitCsv(normalized.sensitiveColumns());
    for (String column : splitCsv(normalized.columns())) {
      mapper.insertColumn(mapper.nextColumnId(), objectId, column, sensitive.contains(column) ? "Y" : "N");
    }
    auditService.record(new AuditEvent("PROTECTED_OBJECT_CREATED", null, objectId, "SUCCESS", null, null,
        normalized.objectName()));
  }

  @Transactional
  public ProtectedObject ensureProtectedObject(String owner, String objectName) {
    ProtectedObject existing = mapper.findByOwnerAndName(owner, objectName);
    if (existing != null) {
      return existing;
    }
    List<String> columns = mapper.findDatabaseColumns(owner, objectName);
    if (columns.isEmpty()) {
      throw new AppException("DB 객체 컬럼을 찾을 수 없습니다: " + owner + "." + objectName);
    }
    long objectId = mapper.nextObjectId();
    mapper.insertObject(objectId, new ProtectedObjectCreateCommand(
        owner,
        objectName,
        defaultOrdsPath(owner, objectName),
        String.join(",", columns),
        ""
    ));
    for (String column : columns) {
      mapper.insertColumn(mapper.nextColumnId(), objectId, column, "N");
    }
    auditService.record(new AuditEvent("PROTECTED_OBJECT_CREATED", null, objectId, "SUCCESS", null, null,
        owner + "." + objectName));
    return mapper.findById(objectId);
  }

  private String defaultOrdsPath(String owner, String objectName) {
    String schemaPath = owner.equalsIgnoreCase("CB_ORDS") ? "cb-ords" : owner.toLowerCase(Locale.ROOT);
    return schemaPath + "/" + objectName.toLowerCase(Locale.ROOT);
  }

  private ProtectedObjectCreateCommand normalizeCreateCommand(ProtectedObjectCreateCommand command) {
    String owner = command.owner().trim().toUpperCase(Locale.ROOT);
    String objectName = command.objectName().trim().toUpperCase(Locale.ROOT);
    String ordsPath = command.ordsPath();
    if (ordsPath == null || ordsPath.isBlank()) {
      ordsPath = defaultOrdsPath(owner, objectName);
    }
    String columns = command.columns();
    if (columns == null || columns.isBlank()) {
      columns = String.join(",", mapper.findDatabaseColumns(owner, objectName));
    }
    return new ProtectedObjectCreateCommand(owner, objectName, ordsPath.trim(), columns, command.sensitiveColumns());
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
