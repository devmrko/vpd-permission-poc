package com.cloudhandson.vpdbackoffice.mapper;

import com.cloudhandson.vpdbackoffice.domain.setting.BackofficeSetting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SettingMapper {

  BackofficeSetting findByKey(@Param("settingKey") String settingKey);

  void upsert(@Param("settingKey") String settingKey, @Param("settingValue") String settingValue);
}
