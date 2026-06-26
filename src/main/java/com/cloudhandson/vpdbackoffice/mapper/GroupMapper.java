package com.cloudhandson.vpdbackoffice.mapper;

import com.cloudhandson.vpdbackoffice.domain.group.AppGroup;
import com.cloudhandson.vpdbackoffice.domain.group.GroupCreateCommand;
import com.cloudhandson.vpdbackoffice.domain.group.GroupRoleView;
import com.cloudhandson.vpdbackoffice.domain.group.GroupUserView;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GroupMapper {

  List<AppGroup> findAll();

  List<GroupUserView> findGroupUsers();

  List<GroupRoleView> findGroupRoles();

  long nextGroupId();

  void insertGroup(@Param("groupId") long groupId, @Param("command") GroupCreateCommand command);

  int updateActive(@Param("groupId") long groupId, @Param("activeYn") String activeYn);

  void insertGroupUser(@Param("groupId") long groupId, @Param("userId") long userId);

  int deleteGroupUser(@Param("groupId") long groupId, @Param("userId") long userId);

  void insertGroupRole(@Param("groupId") long groupId, @Param("roleId") long roleId);

  int deleteGroupRole(@Param("groupId") long groupId, @Param("roleId") long roleId);
}
