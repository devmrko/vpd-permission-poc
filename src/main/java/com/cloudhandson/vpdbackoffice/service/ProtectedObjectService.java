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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProtectedObjectService {

  private final ProtectedObjectMapper mapper;
  private final AuditService auditService;
  private volatile CacheEntry<List<DatabaseObjectOption>> databaseObjectsCache;
  private final Map<String, CacheEntry<List<String>>> databaseColumnsCache = new ConcurrentHashMap<>();
  private final Map<Long, CacheEntry<List<ProtectedColumn>>> protectedColumnsCache = new ConcurrentHashMap<>();
  private static final long CATALOG_CACHE_MILLIS = 60_000L;
  private static final Set<String> SENSITIVITY_LEVELS = Set.of(
      "PUBLIC", "INTERNAL", "CONFIDENTIAL", "RESTRICTED");
  private static final Set<String> REDACTION_METHODS = Set.of("NONE", "NULLIFY", "PARTIAL", "FULL");

  public ProtectedObjectService(ProtectedObjectMapper mapper, AuditService auditService) {
    this.mapper = mapper;
    this.auditService = auditService;
  }

  public List<ProtectedObject> findEnabled() {
    return mapper.findEnabled();
  }

  public List<ProtectedObject> findEnabledWithPermissions() {
    return mapper.findEnabledWithPermissions();
  }

  public List<DatabaseObjectOption> findDatabaseObjects() {
    CacheEntry<List<DatabaseObjectOption>> cached = databaseObjectsCache;
    if (cached != null && !cached.expired()) {
      return cached.value();
    }
    List<DatabaseObjectOption> objects = List.copyOf(mapper.findDatabaseObjects());
    databaseObjectsCache = new CacheEntry<>(objects, System.currentTimeMillis() + CATALOG_CACHE_MILLIS);
    return objects;
  }

  public List<String> findDatabaseColumns(String owner, String objectName) {
    String key = owner.trim().toUpperCase(Locale.ROOT) + "." + objectName.trim().toUpperCase(Locale.ROOT);
    CacheEntry<List<String>> cached = databaseColumnsCache.get(key);
    if (cached != null && !cached.expired()) {
      return cached.value();
    }
    List<String> columns = List.copyOf(mapper.findDatabaseColumns(owner, objectName));
    databaseColumnsCache.put(key, new CacheEntry<>(columns, System.currentTimeMillis() + CATALOG_CACHE_MILLIS));
    return columns;
  }

  public ProtectedObject assertEnabled(long objectId) {
    ProtectedObject object = mapper.findById(objectId);
    if (object == null || !object.enabled()) {
      throw new AppException("활성 보호 객체를 찾을 수 없습니다.");
    }
    if (isLegacyAutoPath(object.ordsPath(), object.owner(), object.objectName())) {
      mapper.updateOrdsPath(object.objectId(), defaultOrdsPath(object.owner(), object.objectName()));
      databaseObjectsCache = null;
      return mapper.findById(object.objectId());
    }
    return object;
  }

  public List<ProtectedColumn> findColumns(long objectId) {
    CacheEntry<List<ProtectedColumn>> cached = protectedColumnsCache.get(objectId);
    if (cached != null && !cached.expired()) {
      return cached.value();
    }
    List<ProtectedColumn> columns = List.copyOf(mapper.findColumns(objectId));
    protectedColumnsCache.put(objectId, new CacheEntry<>(columns, System.currentTimeMillis() + CATALOG_CACHE_MILLIS));
    return columns;
  }

  @Transactional
  public void createObject(ProtectedObjectCreateCommand command) {
    ProtectedObjectCreateCommand normalized = normalizeCreateCommand(command);
    long objectId = mapper.nextObjectId();
    mapper.insertObject(objectId, normalized);
    Set<String> sensitive = splitCsv(normalized.sensitiveColumns());
    for (String column : splitCsv(normalized.columns())) {
      String sensitiveYn = sensitive.contains(column) ? "Y" : "N";
      mapper.insertColumn(mapper.nextColumnId(), objectId, column, sensitiveYn,
          defaultSensitivityLevel(sensitiveYn), defaultRedactionMethod(sensitiveYn));
    }
    databaseObjectsCache = null;
    protectedColumnsCache.remove(objectId);
    auditService.record(new AuditEvent("PROTECTED_OBJECT_CREATED", null, objectId, "SUCCESS", null, null,
        normalized.objectName()));
  }

  @Transactional
  public ProtectedObject ensureProtectedObject(String owner, String objectName) {
    String normalizedOwner = owner.trim().toUpperCase(Locale.ROOT);
    String normalizedObjectName = objectName.trim().toUpperCase(Locale.ROOT);
    ProtectedObject existing = mapper.findByOwnerAndName(normalizedOwner, normalizedObjectName);
    if (existing != null) {
      if (!existing.enabled()) {
        mapper.enableObject(existing.objectId());
        if (isLegacyAutoPath(existing.ordsPath(), normalizedOwner, normalizedObjectName)) {
          mapper.updateOrdsPath(existing.objectId(), defaultOrdsPath(normalizedOwner, normalizedObjectName));
        }
        databaseObjectsCache = null;
        protectedColumnsCache.remove(existing.objectId());
        auditService.record(new AuditEvent("PROTECTED_OBJECT_RE_ENABLED", null, existing.objectId(), "SUCCESS", null,
            null, existing.displayName()));
        return mapper.findById(existing.objectId());
      }
      if (isLegacyAutoPath(existing.ordsPath(), normalizedOwner, normalizedObjectName)) {
        mapper.updateOrdsPath(existing.objectId(), defaultOrdsPath(normalizedOwner, normalizedObjectName));
        databaseObjectsCache = null;
        return mapper.findById(existing.objectId());
      }
      return existing;
    }
    List<String> columns = findDatabaseColumns(normalizedOwner, normalizedObjectName);
    if (columns.isEmpty()) {
      throw new AppException("DB에서 컬럼 정보를 찾을 수 없습니다. 객체명과 스키마를 확인하세요: "
          + normalizedOwner + "." + normalizedObjectName);
    }
    ProtectedObjectCreateCommand command = new ProtectedObjectCreateCommand(
        normalizedOwner,
        normalizedObjectName,
        defaultOrdsPath(normalizedOwner, normalizedObjectName),
        String.join(",", columns),
        ""
    );
    long objectId = mapper.nextObjectId();
    mapper.insertObject(objectId, command);
    for (String column : columns) {
      mapper.insertColumn(mapper.nextColumnId(), objectId, column, "N", "PUBLIC", "NONE");
    }
    databaseObjectsCache = null;
    protectedColumnsCache.remove(objectId);
    auditService.record(new AuditEvent("PROTECTED_OBJECT_AUTO_CREATED", null, objectId, "SUCCESS", null, null,
        command.objectName()));
    return mapper.findById(objectId);
  }

  private String defaultOrdsPath(String owner, String objectName) {
    return "cb-ords/cb-object-query/"
        + owner.toLowerCase(Locale.ROOT) + "/"
        + objectName.toLowerCase(Locale.ROOT);
  }

  private boolean isLegacyAutoPath(String ordsPath, String owner, String objectName) {
    if (ordsPath == null) {
      return true;
    }
    String legacyPath = owner.toLowerCase(Locale.ROOT) + "/" + objectName.toLowerCase(Locale.ROOT);
    return legacyPath.equals(ordsPath.trim());
  }

  private ProtectedObjectCreateCommand normalizeCreateCommand(ProtectedObjectCreateCommand command) {
    String owner = command.owner().trim().toUpperCase(Locale.ROOT);
    String objectName = command.objectName().trim().toUpperCase(Locale.ROOT);
    String ordsPath = command.ordsPath();
    if (ordsPath == null || ordsPath.isBlank()) {
      throw new AppException("ORDS Path는 실제 ORDS module/template 경로를 입력해야 합니다.");
    }
    String columns = command.columns();
    if (columns == null || columns.isBlank()) {
      columns = String.join(",", mapper.findDatabaseColumns(owner, objectName));
    }
    return new ProtectedObjectCreateCommand(owner, objectName, ordsPath.trim(), columns, command.sensitiveColumns());
  }

  @Transactional
  public void updateOrdsPath(long objectId, String ordsPath) {
    if (ordsPath == null || ordsPath.isBlank()) {
      throw new AppException("ORDS Path는 필수입니다.");
    }
    int updated = mapper.updateOrdsPath(objectId, ordsPath.trim());
    if (updated == 0) {
      throw new AppException("보호 객체를 찾을 수 없습니다.");
    }
    auditService.record(new AuditEvent("PROTECTED_OBJECT_ORDS_PATH_UPDATED", null, objectId, "SUCCESS", null, null,
        ordsPath.trim()));
  }

  @Transactional
  public void updateColumnPolicy(long columnId, String sensitivityLevel, String redactionMethod) {
    String normalizedLevel = normalizeOption(sensitivityLevel, "PUBLIC", SENSITIVITY_LEVELS, "민감도 등급");
    String normalizedMethod = normalizeOption(redactionMethod, "NONE", REDACTION_METHODS, "마스킹 방식");
    if ("PUBLIC".equals(normalizedLevel) && !"NONE".equals(normalizedMethod)) {
      throw new AppException("PUBLIC 컬럼의 마스킹 방식은 NONE이어야 합니다.");
    }
    int updated = mapper.updateColumnPolicy(columnId, normalizedLevel, normalizedMethod);
    if (updated == 0) {
      throw new AppException("수정할 컬럼 정책을 찾을 수 없습니다.");
    }
    protectedColumnsCache.clear();
    auditService.record(new AuditEvent("PROTECTED_COLUMN_POLICY_UPDATED", null, null, "SUCCESS", null, null,
        "columnId=" + columnId + ", " + normalizedLevel + "/" + normalizedMethod));
  }

  @Transactional
  public void disableObject(long objectId) {
    int updated = mapper.disableObject(objectId);
    if (updated == 0) {
      throw new AppException("보호 객체를 찾을 수 없습니다.");
    }
    databaseObjectsCache = null;
    protectedColumnsCache.remove(objectId);
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

  private String defaultSensitivityLevel(String sensitiveYn) {
    return "Y".equalsIgnoreCase(sensitiveYn) ? "CONFIDENTIAL" : "PUBLIC";
  }

  private String defaultRedactionMethod(String sensitiveYn) {
    return "Y".equalsIgnoreCase(sensitiveYn) ? "NULLIFY" : "NONE";
  }

  private String normalizeOption(String value, String defaultValue, Set<String> allowed, String label) {
    String normalized = value == null || value.isBlank()
        ? defaultValue
        : value.trim().toUpperCase(Locale.ROOT);
    if (!allowed.contains(normalized)) {
      throw new AppException("허용되지 않은 " + label + "입니다: " + normalized);
    }
    return normalized;
  }

  private record CacheEntry<T>(T value, long expiresAt) {

    boolean expired() {
      return System.currentTimeMillis() > expiresAt;
    }
  }
}
