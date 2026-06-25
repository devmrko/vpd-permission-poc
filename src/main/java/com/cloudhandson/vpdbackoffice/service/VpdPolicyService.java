package com.cloudhandson.vpdbackoffice.service;

import com.cloudhandson.vpdbackoffice.domain.vpd.VpdFunctionSource;
import com.cloudhandson.vpdbackoffice.domain.vpd.VpdPolicyDetail;
import com.cloudhandson.vpdbackoffice.domain.vpd.VpdPolicyExplanation;
import com.cloudhandson.vpdbackoffice.domain.vpd.VpdPolicyView;
import com.cloudhandson.vpdbackoffice.mapper.VpdPolicyMapper;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class VpdPolicyService {

  private final VpdPolicyMapper mapper;
  private final OpenAiCompatibleClient aiClient;

  public VpdPolicyService(VpdPolicyMapper mapper, OpenAiCompatibleClient aiClient) {
    this.mapper = mapper;
    this.aiClient = aiClient;
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

  public VpdPolicyExplanation explainPolicy(String objectOwner, String objectName, String policyName) {
    VpdPolicyDetail detail = findPolicyDetail(objectOwner, objectName, policyName);
    VpdPolicyView policy = detail.policy();
    VpdFunctionSource functionSource = findFunctionSource(
        policy.functionOwner(),
        policy.packageName(),
        policy.functionName()
    );
    String prompt = buildExplanationPrompt(detail, functionSource);
    if (!aiClient.configured()) {
      return new VpdPolicyExplanation(
          "AI_NOT_CONFIGURED",
          aiClient.modelName(),
          "AI base URL/API Key 설정이 없어 모델 호출은 건너뛰었습니다. 아래 Prompt와 function source를 기준으로 policy/filter 내용을 확인하세요.",
          prompt,
          detail,
          functionSource
      );
    }
    try {
      String answer = aiClient.chat(vpdSystemPrompt(), prompt);
      return new VpdPolicyExplanation("SUCCESS", aiClient.modelName(), answer, prompt, detail, functionSource);
    } catch (Exception e) {
      return new VpdPolicyExplanation(
          "AI_CALL_FAILED",
          aiClient.modelName(),
          "AI 호출은 실패했지만 policy/filter 근거는 수집했습니다. 상세: " + e.getMessage(),
          prompt,
          detail,
          functionSource
      );
    }
  }

  private String buildExplanationPrompt(VpdPolicyDetail detail, VpdFunctionSource functionSource) {
    VpdPolicyView policy = detail.policy();
    return """
        다음 Oracle VPD policy와 policy function source를 근거로 설명해줘.

        Policy Metadata:
        - Object: %s
        - Policy Group: %s
        - Policy Name: %s
        - Function: %s
        - Statement Types: %s
        - Enabled: %s
        - Policy Type: %s
        - Check Option: %s
        - Static Policy: %s
        - Long Predicate: %s

        DBMS_RLS.ADD_POLICY:
        %s

        Policy Function Source:
        %s

        답변 요구사항:
        - 한국어로 답변한다.
        - 이 policy가 어느 객체의 어떤 SQL 동작에 적용되는지 설명한다.
        - filter predicate가 어떤 조건을 만들고 어떤 행이 보이거나 제외되는지 설명한다.
        - source에 명시되지 않은 동작은 추측하지 않는다.
        - 운영자가 확인할 포인트를 짧게 정리한다.
        """.formatted(
        policy.objectDisplayName(),
        policy.policyGroup(),
        policy.policyName(),
        policy.functionDisplayName(),
        blankToDefault(policy.statementTypes(), "-"),
        policy.enabled(),
        policy.policyType(),
        policy.checkOption(),
        policy.staticPolicy(),
        policy.longPredicate(),
        detail.ddl(),
        functionSource.found() ? functionSource.source() : "ALL_SOURCE에서 조회 가능한 source가 없습니다."
    );
  }

  private String vpdSystemPrompt() {
    return """
        당신은 Oracle ADB VPD(DBMS_RLS), RLS policy function, ORDS 권한 검증을 설명하는 보조자입니다.
        제공된 policy metadata와 source만 근거로 설명하고, 없는 정보를 추측하지 마세요.
        """;
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
