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

  public ProtectedObjectService(ProtectedObjectMapper mapper, AuditService auditService) {
    this.mapper = mapper;
    this.auditService = auditService;
  }

  public List<ProtectedObject> findEnabled() {
    return mapper.findEnabled();
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
      mapper.insertColumn(mapper.nextColumnId(), objectId, column, sensitive.contains(column) ? "Y" : "N");
    }
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
        auditService.record(new AuditEvent("PROTECTED_OBJECT_RE_ENABLED", null, existing.objectId(), "SUCCESS", null,
            null, existing.displayName()));
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
      mapper.insertColumn(mapper.nextColumnId(), objectId, column, "N");
    }
    protectedColumnsCache.remove(objectId);
    auditService.record(new AuditEvent("PROTECTED_OBJECT_AUTO_CREATED", null, objectId, "SUCCESS", null, null,
        command.objectName()));
    return mapper.findById(objectId);
  }

  private String defaultOrdsPath(String owner, String objectName) {
    return owner.toLowerCase(Locale.ROOT) + "/" + objectName.toLowerCase(Locale.ROOT);
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

  private record CacheEntry<T>(T value, long expiresAt) {

    boolean expired() {
      return System.currentTimeMillis() > expiresAt;
    }
  }
}
