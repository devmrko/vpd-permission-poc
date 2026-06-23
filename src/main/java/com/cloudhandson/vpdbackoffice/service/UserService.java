package com.cloudhandson.vpdbackoffice.service;

import com.cloudhandson.vpdbackoffice.domain.audit.AuditEvent;
import com.cloudhandson.vpdbackoffice.domain.user.AppUser;
import com.cloudhandson.vpdbackoffice.domain.user.UserCreateCommand;
import com.cloudhandson.vpdbackoffice.domain.user.UserRoleView;
import com.cloudhandson.vpdbackoffice.mapper.UserMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

  private final UserMapper userMapper;
  private final AuditService auditService;

  public UserService(UserMapper userMapper, AuditService auditService) {
    this.userMapper = userMapper;
    this.auditService = auditService;
  }

  public List<AppUser> findAll() {
    return userMapper.findAll();
  }

  public List<UserRoleView> findUserRoles() {
    return userMapper.findUserRoles();
  }

  @Transactional
  public void createUser(UserCreateCommand command) {
    long userId = userMapper.nextUserId();
    userMapper.insertUser(userId, command);
    auditService.record(new AuditEvent("USER_CREATED", null, null, "SUCCESS", null, null, command.username()));
  }

  @Transactional
  public void setActive(long userId, boolean active) {
    int updated = userMapper.updateActive(userId, active ? "Y" : "N");
    if (updated == 0) {
      throw new AppException("사용자를 찾을 수 없습니다.");
    }
    auditService.record(new AuditEvent("USER_ACTIVE_CHANGED", null, null, "SUCCESS", null, null,
        "userId=" + userId + ",active=" + active));
  }

  @Transactional
  public void grantRole(long userId, long roleId) {
    userMapper.insertUserRole(userId, roleId);
    auditService.record(new AuditEvent("USER_ROLE_GRANTED", null, null, "SUCCESS", null, null,
        "userId=" + userId + ",roleId=" + roleId));
  }

  @Transactional
  public void revokeRole(long userId, long roleId) {
    int deleted = userMapper.deleteUserRole(userId, roleId);
    if (deleted == 0) {
      throw new AppException("삭제할 사용자 역할 매핑을 찾을 수 없습니다.");
    }
    auditService.record(new AuditEvent("USER_ROLE_REVOKED", null, null, "SUCCESS", null, null,
        "userId=" + userId + ",roleId=" + roleId));
  }
}
