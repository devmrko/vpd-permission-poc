package com.cloudhandson.vpdbackoffice.service;

import com.cloudhandson.vpdbackoffice.domain.audit.AuditEvent;
import com.cloudhandson.vpdbackoffice.domain.group.AppGroup;
import com.cloudhandson.vpdbackoffice.domain.group.GroupCreateCommand;
import com.cloudhandson.vpdbackoffice.domain.group.GroupRoleView;
import com.cloudhandson.vpdbackoffice.domain.group.GroupUserView;
import com.cloudhandson.vpdbackoffice.mapper.GroupMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupService {

  private final GroupMapper groupMapper;
  private final AuditService auditService;

  public GroupService(GroupMapper groupMapper, AuditService auditService) {
    this.groupMapper = groupMapper;
    this.auditService = auditService;
  }

  public List<AppGroup> findAll() {
    return groupMapper.findAll();
  }

  public List<GroupUserView> findGroupUsers() {
    return groupMapper.findGroupUsers();
  }

  public List<GroupRoleView> findGroupRoles() {
    return groupMapper.findGroupRoles();
  }

  @Transactional
  public void createGroup(GroupCreateCommand command) {
    long groupId = groupMapper.nextGroupId();
    groupMapper.insertGroup(groupId, command);
    auditService.record(new AuditEvent("GROUP_CREATED", null, null, "SUCCESS", null, null, command.groupCode()));
  }

  @Transactional
  public void setActive(long groupId, boolean active) {
    int updated = groupMapper.updateActive(groupId, active ? "Y" : "N");
    if (updated == 0) {
      throw new AppException("그룹을 찾을 수 없습니다.");
    }
    auditService.record(new AuditEvent("GROUP_ACTIVE_CHANGED", null, null, "SUCCESS", null, null,
        "groupId=" + groupId + ",active=" + active));
  }

  @Transactional
  public void addUser(long groupId, long userId) {
    groupMapper.insertGroupUser(groupId, userId);
    auditService.record(new AuditEvent("GROUP_USER_ADDED", null, null, "SUCCESS", null, null,
        "groupId=" + groupId + ",userId=" + userId));
  }

  @Transactional
  public void removeUser(long groupId, long userId) {
    int deleted = groupMapper.deleteGroupUser(groupId, userId);
    if (deleted == 0) {
      throw new AppException("삭제할 그룹 사용자 매핑을 찾을 수 없습니다.");
    }
    auditService.record(new AuditEvent("GROUP_USER_REMOVED", null, null, "SUCCESS", null, null,
        "groupId=" + groupId + ",userId=" + userId));
  }

  @Transactional
  public void addRole(long groupId, long roleId) {
    groupMapper.insertGroupRole(groupId, roleId);
    auditService.record(new AuditEvent("GROUP_ROLE_ADDED", null, null, "SUCCESS", null, null,
        "groupId=" + groupId + ",roleId=" + roleId));
  }

  @Transactional
  public void removeRole(long groupId, long roleId) {
    int deleted = groupMapper.deleteGroupRole(groupId, roleId);
    if (deleted == 0) {
      throw new AppException("삭제할 그룹 역할 매핑을 찾을 수 없습니다.");
    }
    auditService.record(new AuditEvent("GROUP_ROLE_REMOVED", null, null, "SUCCESS", null, null,
        "groupId=" + groupId + ",roleId=" + roleId));
  }
}
