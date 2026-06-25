package com.cloudhandson.vpdbackoffice.service;

import com.cloudhandson.vpdbackoffice.domain.vpd.VpdFunctionSource;
import com.cloudhandson.vpdbackoffice.domain.vpd.VpdPolicyDetail;
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

  public VpdPolicyDetail findPolicyDetail(String objectOwner, String objectName, String policyName) {
    String normalizedOwner = requiredIdentifier(objectOwner, "Object owner");
    String normalizedObject = requiredIdentifier(objectName, "Object name");
    String normalizedPolicy = requiredIdentifier(policyName, "Policy name");
    VpdPolicyView policy = mapper.findPolicy(normalizedOwner, normalizedObject, normalizedPolicy);
    if (policy == null) {
      throw new AppException("VPD policy를 찾을 수 없습니다.");
    }
    return new VpdPolicyDetail(policy, buildAddPolicyBlock(policy));
  }

  private String buildAddPolicyBlock(VpdPolicyView policy) {
    return """
        BEGIN
          DBMS_RLS.ADD_POLICY(
            object_schema   => '%s',
            object_name     => '%s',
            policy_name     => '%s',
            function_schema => '%s',
            policy_function => '%s',
            statement_types => '%s',
            update_check    => %s,
            enable          => %s,
            static_policy   => %s,
            policy_type     => %s,
            long_predicate  => %s
          );
        END;
        /
        """.formatted(
        policy.objectOwner(),
        policy.objectName(),
        policy.policyName(),
        policy.functionOwner(),
        policyFunctionArgument(policy),
        blankToDefault(policy.statementTypes(), "SELECT"),
        yesNoBoolean(policy.checkOption()),
        yesNoBoolean(policy.enabled()),
        yesNoBoolean(policy.staticPolicy()),
        policyTypeArgument(policy.policyType()),
        yesNoBoolean(policy.longPredicate())
    );
  }

  private String policyFunctionArgument(VpdPolicyView policy) {
    if (policy.packageName() == null || policy.packageName().isBlank()) {
      return policy.functionName();
    }
    return policy.packageName() + "." + policy.functionName();
  }

  private String blankToDefault(String value, String defaultValue) {
    return value == null || value.isBlank() ? defaultValue : value;
  }

  private String yesNoBoolean(String value) {
    return "YES".equalsIgnoreCase(value) ? "TRUE" : "FALSE";
  }

  private String policyTypeArgument(String policyType) {
    if (policyType == null || policyType.isBlank()) {
      return "NULL";
    }
    return "DBMS_RLS." + policyType.trim().toUpperCase(Locale.ROOT);
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
