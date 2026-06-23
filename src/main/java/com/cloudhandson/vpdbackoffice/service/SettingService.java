package com.cloudhandson.vpdbackoffice.service;

import com.cloudhandson.vpdbackoffice.config.BackofficeProperties;
import com.cloudhandson.vpdbackoffice.domain.setting.BackofficeSetting;
import com.cloudhandson.vpdbackoffice.mapper.SettingMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingService {

  public static final String ORDS_BASE_URL = "ORDS_BASE_URL";

  private final SettingMapper settingMapper;
  private final BackofficeProperties properties;

  public SettingService(SettingMapper settingMapper, BackofficeProperties properties) {
    this.settingMapper = settingMapper;
    this.properties = properties;
  }

  public String ordsBaseUrl() {
    BackofficeSetting setting = settingMapper.findByKey(ORDS_BASE_URL);
    if (setting != null && setting.settingValue() != null && !setting.settingValue().isBlank()) {
      return normalizeUrl(setting.settingValue());
    }
    return normalizeUrl(properties.ords().baseUrl());
  }

  @Transactional
  public void updateOrdsBaseUrl(String value) {
    if (value == null || value.isBlank()) {
      throw new AppException("ORDS Base URL은 필수입니다.");
    }
    String normalized = normalizeUrl(value);
    if (!normalized.startsWith("https://") && !normalized.startsWith("http://")) {
      throw new AppException("ORDS Base URL은 http:// 또는 https://로 시작해야 합니다.");
    }
    settingMapper.upsert(ORDS_BASE_URL, normalized);
  }

  private String normalizeUrl(String value) {
    if (value == null) {
      return "";
    }
    String trimmed = value.trim();
    while (trimmed.endsWith("/") && trimmed.length() > "https://".length()) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed;
  }
}
