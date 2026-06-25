package com.cloudhandson.vpdbackoffice.service;

import com.cloudhandson.vpdbackoffice.domain.vpd.VpdFunctionSource;
import com.cloudhandson.vpdbackoffice.domain.vpd.VpdPolicyView;
import com.cloudhandson.vpdbackoffice.mapper.VpdPolicyMapper;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class VpdPolicyService {

  private final VpdPolicyMapper mapper;

  public VpdPolicyService(VpdPolicyMapper mapper) {
    this.mapper = mapper;
  }

  public List<VpdPolicyView> findPolicies() {
    return mapper.findPolicies();
  }

  public VpdFunctionSource findFunctionSource(String owner, String packageName, String functionName) {
    String normalizedOwner = requiredIdentifier(owner, "Function owner");
    String normalizedFunction = requiredIdentifier(functionName, "Function name");
    String normalizedPackage = normalizeOptionalIdentifier(packageName);
    String objectName = normalizedPackage == null ? normalizedFunction : normalizedPackage;
    String objectType = normalizedPackage == null ? "FUNCTION" : "PACKAGE BODY";
    String source = mapper.findFunctionSource(normalizedOwner, objectName, objectType);
    return new VpdFunctionSource(normalizedOwner, objectName, objectType, source);
  }

  private String requiredIdentifier(String value, String label) {
    String normalized = normalizeOptionalIdentifier(value);
    if (normalized == null) {
      throw new AppException(label + " 값이 없습니다.");
    }
    return normalized;
  }

  private String normalizeOptionalIdentifier(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    if (!normalized.matches("[A-Z][A-Z0-9_$#]{0,127}")) {
      throw new AppException("Oracle identifier 형식이 올바르지 않습니다: " + value);
    }
    return normalized;
  }
}
