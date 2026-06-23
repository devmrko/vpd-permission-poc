package com.cloudhandson.vpdbackoffice.service;

import com.cloudhandson.vpdbackoffice.domain.audit.AuditEvent;
import com.cloudhandson.vpdbackoffice.domain.permission.AppRole;
import com.cloudhandson.vpdbackoffice.domain.permission.PermissionRule;
import com.cloudhandson.vpdbackoffice.domain.permission.PermissionSet;
import com.cloudhandson.vpdbackoffice.domain.permission.PermissionSetCommand;
import com.cloudhandson.vpdbackoffice.domain.permission.RuleCommand;
import com.cloudhandson.vpdbackoffice.domain.protectedobject.ProtectedColumn;
import com.cloudhandson.vpdbackoffice.mapper.PermissionMapper;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PermissionService {

  private static final Set<String> RULE_TYPES = Set.of("ALL", "MY_DEPT", "SELF", "REGION");

  private final PermissionMapper permissionMapper;
  private final ProtectedObjectService protectedObjectService;
  private final AuditService auditService;

  public PermissionService(
      PermissionMapper permissionMapper,
      ProtectedObjectService protectedObjectService,
      AuditService auditService
  ) {
    this.permissionMapper = permissionMapper;
    this.protectedObjectService = protectedObjectService;
    this.auditService = auditService;
  }

  public List<AppRole> findRoles() {
    return permissionMapper.findRoles();
  }

  @Transactional
  public PermissionSet savePermissionSet(PermissionSetCommand command) {
    if (!"SELECT".equalsIgnoreCase(command.action())) {
      throw new AppException("초기 구현에서는 SELECT 권한만 저장할 수 있습니다.");
    }
    AppRole role = permissionMapper.findRole(command.roleId());
    if (role == null) {
      throw new AppException("역할을 찾을 수 없습니다.");
    }
    protectedObjectService.assertEnabled(command.objectId());
    validateRules(command.rules());
    validateVisibleColumns(command.objectId(), command.visibleColumns());

    Long existingId = permissionMapper.findPermissionId(command.roleId(), command.objectId());
    long permissionId = existingId == null ? permissionMapper.nextPermissionId() : existingId;
    if (existingId == null) {
      permissionMapper.insertPermission(permissionId, command.roleId(), command.objectId(), "SELECT");
    } else {
      permissionMapper.updatePermissionAction(permissionId, "SELECT");
    }

    permissionMapper.deleteRules(permissionId);
    for (RuleCommand rule : command.rules()) {
      permissionMapper.insertRule(new PermissionRule(
          permissionMapper.nextRuleId(),
          permissionId,
          normalize(rule.ruleType()),
          clean(rule.ruleValue())
      ));
    }

    permissionMapper.deleteVisibleColumns(permissionId);
    if (command.visibleColumns() != null) {
      for (String columnName : command.visibleColumns()) {
        permissionMapper.insertVisibleColumn(permissionId, columnName.trim().toUpperCase(Locale.ROOT));
      }
    }

    auditService.record(new AuditEvent(
        "PERMISSION_SAVED", null, command.objectId(), "SUCCESS", null, null,
        "roleId=" + command.roleId()
    ));
    return new PermissionSet(permissionId, command.roleId(), command.objectId(), "SELECT", List.of(), List.of());
  }

  private void validateRules(List<RuleCommand> rules) {
    if (rules == null || rules.isEmpty()) {
      throw new AppException("행 규칙은 하나 이상 필요합니다.");
    }
    Set<String> seen = new HashSet<>();
    boolean hasAll = false;
    for (RuleCommand rule : rules) {
      String type = normalize(rule.ruleType());
      if (!RULE_TYPES.contains(type)) {
        throw new AppException("허용되지 않은 행 규칙입니다: " + type);
      }
      if (!seen.add(type + ":" + clean(rule.ruleValue()))) {
        throw new AppException("중복된 행 규칙이 있습니다.");
      }
      hasAll = hasAll || "ALL".equals(type);
      if (!"ALL".equals(type) && clean(rule.ruleValue()).isBlank()) {
        throw new AppException(type + " 규칙에는 값이 필요합니다.");
      }
    }
    if (hasAll && rules.size() > 1) {
      throw new AppException("ALL 규칙은 다른 규칙과 함께 저장할 수 없습니다.");
    }
  }

  private void validateVisibleColumns(long objectId, List<String> visibleColumns) {
    if (visibleColumns == null || visibleColumns.isEmpty()) {
      return;
    }
    Set<String> allowed = new HashSet<>();
    for (ProtectedColumn column : protectedObjectService.findColumns(objectId)) {
      allowed.add(column.columnName().toUpperCase(Locale.ROOT));
    }
    for (String columnName : visibleColumns) {
      if (!allowed.contains(columnName.trim().toUpperCase(Locale.ROOT))) {
        throw new AppException("등록되지 않은 컬럼입니다: " + columnName);
      }
    }
  }

  private String normalize(String value) {
    return clean(value).toUpperCase(Locale.ROOT);
  }

  private String clean(String value) {
    return value == null ? "" : value.trim();
  }
}
