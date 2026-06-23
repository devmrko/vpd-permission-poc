package com.cloudhandson.vpdbackoffice.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cloudhandson.vpdbackoffice.domain.audit.AuditEvent;
import com.cloudhandson.vpdbackoffice.domain.permission.AppRole;
import com.cloudhandson.vpdbackoffice.domain.permission.PermissionRule;
import com.cloudhandson.vpdbackoffice.domain.permission.PermissionSet;
import com.cloudhandson.vpdbackoffice.domain.permission.PermissionSetCommand;
import com.cloudhandson.vpdbackoffice.domain.permission.PermissionView;
import com.cloudhandson.vpdbackoffice.domain.permission.RuleCommand;
import com.cloudhandson.vpdbackoffice.domain.protectedobject.ProtectedColumn;
import com.cloudhandson.vpdbackoffice.domain.protectedobject.ProtectedObject;
import com.cloudhandson.vpdbackoffice.mapper.AuditMapper;
import com.cloudhandson.vpdbackoffice.mapper.PermissionMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PermissionServiceTest {

  private PermissionMapper permissionMapper;
  private ProtectedObjectService protectedObjectService;
  private PermissionService permissionService;

  @BeforeEach
  void setUp() {
    permissionMapper = new FakePermissionMapper();
    AuditService auditService = new AuditService(new NoopAuditMapper());
    protectedObjectService = new ProtectedObjectService(null, auditService) {
      @Override
      public ProtectedObject assertEnabled(long objectId) {
        return new ProtectedObject(1L, "ADMIN", "CB_V_SEARCH_DOCUMENTS", "cb-agent-security/vpd/documents", "Y");
      }

      @Override
      public List<ProtectedColumn> findColumns(long objectId) {
        return List.of();
      }
    };
    permissionService = new PermissionService(permissionMapper, protectedObjectService, auditService);
  }

  @Test
  void rejectsAllRuleMixedWithSpecificRule() {
    var command = new PermissionSetCommand(
        10L,
        1L,
        "SELECT",
        List.of(new RuleCommand("ALL", null), new RuleCommand("REGION", "APAC")),
        List.of()
    );

    assertThatThrownBy(() -> permissionService.savePermissionSet(command))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("ALL");
  }

  @Test
  void rejectsCustomPredicateRule() {
    var command = new PermissionSetCommand(
        10L,
        1L,
        "SELECT",
        List.of(new RuleCommand("CUSTOM_PREDICATE", "1=1")),
        List.of()
    );

    assertThatThrownBy(() -> permissionService.savePermissionSet(command))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("허용되지 않은");
  }

  private static class NoopAuditMapper implements AuditMapper {
    @Override
    public void insert(AuditEvent event) {
    }
  }

  private static class FakePermissionMapper implements PermissionMapper {
    @Override
    public List<AppRole> findRoles() {
      return List.of(new AppRole(10L, "HR_DEPT_ROLE", null));
    }

    @Override
    public AppRole findRole(long roleId) {
      return roleId == 10L ? new AppRole(10L, "HR_DEPT_ROLE", null) : null;
    }

    @Override
    public List<PermissionView> findPermissionViews() {
      return List.of();
    }

    @Override
    public PermissionSet findPermissionSet(long roleId, long objectId) {
      return null;
    }

    @Override
    public Long findPermissionId(long roleId, long objectId) {
      return null;
    }

    @Override
    public void insertPermission(long permissionId, long roleId, long objectId, String action) {
    }

    @Override
    public void updatePermissionAction(long permissionId, String action) {
    }

    @Override
    public void deleteRules(long permissionId) {
    }

    @Override
    public void insertRule(PermissionRule rule) {
    }

    @Override
    public void deleteVisibleColumns(long permissionId) {
    }

    @Override
    public void insertVisibleColumn(long permissionId, String columnName) {
    }

    @Override
    public int deletePermission(long permissionId) {
      return 1;
    }

    @Override
    public long nextPermissionId() {
      return 1000L;
    }

    @Override
    public long nextRuleId() {
      return 10000L;
    }
  }
}
