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
        다음 Oracle VPD policy와 policy function source를 근거로 운영자가 바로 검토할 수 있는 분석 보고서를 작성한다.
        일반적인 VPD 설명은 금지한다. 반드시 제공된 source에서 확인되는 변수, SELECT, IF/ELSIF, RETURN predicate만 근거로 설명한다.

        [입력: Policy Metadata]
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

        [입력: DBMS_RLS.ADD_POLICY]
        %s

        [입력: Policy Function Source]
        %s

        [출력 형식]
        ## 요약
        - 이 policy가 무엇을 허용/차단하는지 3줄 이내로 먼저 설명한다.
        - fail-closed 조건이 있으면 요약에 포함한다.
        - 컬럼 마스킹/NULL 처리 판단 가능 여부를 요약에 포함한다.

        ## 상세

        ### 1. 정책 적용 범위
        - 대상 객체, 적용 SQL, enabled 여부를 한 문단으로 설명한다.

        ### 2. 필터 함수 입력과 조회 값
        - source에서 읽어오는 context/user/role/permission/rule 값을 bullet로 정리한다.
        - 각 항목 옆에 source 근거를 짧게 적는다. 예: "v_user_id: SYS_CONTEXT(...)"

        ### 3. Predicate 생성 분기
        - source의 RETURN 값을 기준으로 Markdown 표를 만든다.
        - 컬럼은 "조건", "반환 predicate", "의미", "보이는 행/차단되는 행"으로 한다.
        - 표는 반드시 Markdown pipe table 형식으로 작성한다.
        - '1=0' 같은 fail-closed predicate가 있으면 반드시 명시한다.

        ### 4. 실제 접근 결과 해석
        - 이 policy가 행(row)을 허용하는 조건과 제외하는 조건을 구분한다.
        - 컬럼 마스킹/NULL 처리는 source에 직접 있지 않으면 "이 policy source만으로는 판단 불가"라고 쓴다.

        ### 5. 운영 확인 포인트
        - 운영자가 DB에서 확인할 테이블/컬럼/컨텍스트 값을 5개 이하로 적는다.

        [엄격한 규칙]
        - 한국어로 답변한다.
        - Markdown 형식으로 답변한다.
        - source에 없는 테이블, 컬럼, role 이름을 만들지 않는다.
        - source 근거가 부족하면 추측하지 말고 "source만으로는 판단 불가"라고 쓴다.
        - "일반적으로", "보통", "아마" 같은 표현을 쓰지 않는다.
        - 답변은 80줄 이내로 유지한다.
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
        당신은 Oracle ADB VPD(DBMS_RLS) policy function을 리뷰하는 데이터 보안 엔지니어입니다.
        목표는 운영자가 실제 filter predicate 동작을 검증할 수 있게 source 기반으로 설명하는 것입니다.
        제공된 policy metadata와 source만 근거로 판단하고, source에 없는 업무 규칙이나 테이블 의미를 추측하지 마세요.
        가능하면 source의 변수명, SELECT 대상, RETURN predicate를 그대로 언급하세요.
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
