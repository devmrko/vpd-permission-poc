package com.cloudhandson.vpdbackoffice.mapper;

import com.cloudhandson.vpdbackoffice.domain.user.AppUser;
import com.cloudhandson.vpdbackoffice.domain.user.UserCreateCommand;
import com.cloudhandson.vpdbackoffice.domain.user.UserRoleView;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

  List<AppUser> findAll();

  List<UserRoleView> findUserRoles();

  AppUser findById(@Param("userId") long userId);

  long nextUserId();

  void insertUser(@Param("userId") long userId, @Param("command") UserCreateCommand command);

  int updateActive(@Param("userId") long userId, @Param("activeYn") String activeYn);

  void insertUserRole(@Param("userId") long userId, @Param("roleId") long roleId);

  int deleteUserRole(@Param("userId") long userId, @Param("roleId") long roleId);
}
