package com.cloudhandson.vpdbackoffice.mapper;

import com.cloudhandson.vpdbackoffice.domain.permission.AppRole;
import com.cloudhandson.vpdbackoffice.domain.permission.PermissionRule;
import com.cloudhandson.vpdbackoffice.domain.permission.PermissionSet;
import com.cloudhandson.vpdbackoffice.domain.permission.PermissionView;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PermissionMapper {

  List<AppRole> findRoles();

  AppRole findRole(@Param("roleId") long roleId);

  List<PermissionView> findPermissionViews();

  PermissionSet findPermissionSet(@Param("roleId") long roleId, @Param("objectId") long objectId);

  Long findPermissionId(@Param("roleId") long roleId, @Param("objectId") long objectId);

  void insertPermission(@Param("permissionId") long permissionId,
                        @Param("roleId") long roleId,
                        @Param("objectId") long objectId,
                        @Param("action") String action);

  void updatePermissionAction(@Param("permissionId") long permissionId, @Param("action") String action);

  void deleteRules(@Param("permissionId") long permissionId);

  void insertRule(PermissionRule rule);

  void deleteVisibleColumns(@Param("permissionId") long permissionId);

  void insertVisibleColumn(@Param("permissionId") long permissionId, @Param("columnName") String columnName);

  int deletePermission(@Param("permissionId") long permissionId);

  long nextPermissionId();

  long nextRuleId();
}
