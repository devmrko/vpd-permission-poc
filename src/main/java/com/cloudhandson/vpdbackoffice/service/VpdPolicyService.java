package com.cloudhandson.vpdbackoffice.service;

import com.cloudhandson.vpdbackoffice.domain.vpd.VpdBulkApplyResult;
import com.cloudhandson.vpdbackoffice.domain.vpd.VpdFunctionSource;
import com.cloudhandson.vpdbackoffice.domain.vpd.VpdPolicyCreateCommand;
import com.cloudhandson.vpdbackoffice.domain.vpd.VpdPolicyDetail;
import com.cloudhandson.vpdbackoffice.domain.vpd.VpdPolicyExplanation;
import com.cloudhandson.vpdbackoffice.domain.vpd.VpdPolicyFormOptions;
import com.cloudhandson.vpdbackoffice.domain.vpd.VpdPolicyView;
import com.cloudhandson.vpdbackoffice.domain.vpd.VpdSchemaObjectOption;
import com.cloudhandson.vpdbackoffice.domain.vpd.VpdTargetView;
import com.cloudhandson.vpdbackoffice.mapper.VpdPolicyMapper;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VpdPolicyService {

  private static final Set<String> ALLOWED_STATEMENTS = Set.of("SELECT", "INSERT", "UPDATE", "DELETE", "INDEX");
  private static final long CATALOG_CACHE_MILLIS = 60_000L;

  private final VpdPolicyMapper mapper;
  private final JdbcTemplate jdbcTemplate;
  private final OpenAiCompatibleClient aiClient;
  private final Map<String, CacheEntry<List<VpdTargetView>>> vpdTargetsCache = new ConcurrentHashMap<>();
  private volatile CacheEntry<VpdPolicyFormOptions> formOptionsCache;

  public VpdPolicyService(VpdPolicyMapper mapper, JdbcTemplate jdbcTemplate, OpenAiCompatibleClient aiClient) {
    this.mapper = mapper;
    this.jdbcTemplate = jdbcTemplate;
    this.aiClient = aiClient;
  }

  public List<VpdPolicyView> findPolicies() {
    return mapper.findPolicies();
  }

  public List<VpdTargetView> findVpdTargets() {
    return findVpdTargets(null);
  }

  public List<VpdTargetView> findVpdTargets(String owner) {
    String normalizedOwner = normalizeOptionalIdentifier(owner);
    String cacheKey = normalizedOwner == null ? "__MANAGED__" : normalizedOwner;
    CacheEntry<List<VpdTargetView>> cached = vpdTargetsCache.get(cacheKey);
    if (cached != null && !cached.expired()) {
      return cached.value();
    }
    List<VpdTargetView> targets = List.copyOf(mapper.findVpdTargets(normalizedOwner));
    vpdTargetsCache.put(cacheKey, new CacheEntry<>(targets, System.currentTimeMillis() + CATALOG_CACHE_MILLIS));
    return targets;
  }

  public VpdPolicyFormOptions formOptions() {
    CacheEntry<VpdPolicyFormOptions> cached = formOptionsCache;
    if (cached != null && !cached.expired()) {
      return cached.value();
    }
    VpdPolicyFormOptions options = new VpdPolicyFormOptions(
        mapper.findPolicyNameOptions(),
        mapper.findSchemaOwnerOptions(),
        mapper.findOwnerOptions(),
        mapper.findFunctionOptions(),
        List.of("SELECT", "INSERT", "UPDATE", "DELETE", "INDEX")
    );
    formOptionsCache = new CacheEntry<>(options, System.currentTimeMillis() + CATALOG_CACHE_MILLIS);
    return options;
  }

  public VpdPolicyFormOptions emptyFormOptions() {
    return new VpdPolicyFormOptions(
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of("SELECT", "INSERT", "UPDATE", "DELETE", "INDEX")
    );
  }

  @Transactional
  public void saveFilterFunction(String functionOwnerValue, String functionNameValue, String filterPredicateValue) {
    String currentUser = jdbcTemplate.queryForObject("SELECT USER FROM dual", String.class);
    String functionOwner = functionOwnerValue == null || functionOwnerValue.isBlank()
        ? currentUser
        : requiredIdentifier(functionOwnerValue, "Function owner");
    if (currentUser == null || !currentUser.equalsIgnoreCase(functionOwner)) {
      throw new AppException("Filter function 등록/수정은 현재 연결 사용자 스키마에만 가능합니다. 현재 사용자: "
          + currentUser + ", Function owner: " + functionOwner);
    }
    String functionName = requiredIdentifier(functionNameValue, "Function name");
    String filterPredicate = filterPredicateValue == null ? "" : filterPredicateValue.trim();
    if (filterPredicate.isBlank()) {
      throw new AppException("Filter predicate는 필수입니다.");
    }
    createFilterFunction(functionName, filterPredicate);
    clearCatalogCache();
  }

  @Transactional
  public void replacePolicy(String oldObjectKey, String oldPolicyName, VpdPolicyCreateCommand command) {
    String[] objectParts = oldObjectKey == null ? new String[0] : oldObjectKey.split("\\.", 2);
    if (objectParts.length != 2) {
      throw new AppException("수정할 Policy 대상 형식이 올바르지 않습니다: " + oldObjectKey);
    }
    String objectOwner = requiredIdentifier(objectParts[0], "Object owner");
    String objectName = requiredIdentifier(objectParts[1], "Object name");
    String policyName = requiredIdentifier(oldPolicyName, "Policy name");
    jdbcTemplate.update("""
        BEGIN
          DBMS_RLS.DROP_POLICY(
            object_schema => ?,
            object_name   => ?,
            policy_name   => ?
          );
        END;
        """, objectOwner, objectName, policyName);
    createPolicy(command);
    clearCatalogCache();
  }

  public VpdBulkApplyResult bulkApplySchema(
      String schemaOwner,
      boolean includeTables,
      boolean includeViews,
      String functionKey,
      String functionOwnerValue,
      String functionNameValue,
      String statementTypesValue,
      boolean enabled,
      boolean updateCheck,
      String filterPredicateValue
  ) {
    String owner = requiredIdentifier(schemaOwner, "Schema");
    if (!includeTables && !includeViews) {
      throw new AppException("TABLE 또는 VIEW 중 하나 이상 선택해야 합니다.");
    }
    List<VpdSchemaObjectOption> targets = mapper.findSchemaObjects(
        owner,
        includeTables ? "Y" : "N",
        includeViews ? "Y" : "N"
    );
    if (targets.isEmpty()) {
      throw new AppException("선택한 스키마에서 VPD 적용 대상 TABLE/VIEW를 찾을 수 없습니다: " + owner);
    }

    FunctionRef functionRef = parseFunctionRef(defaultFunctionKey(functionKey));
    String filterPredicate = filterPredicateValue == null ? "" : filterPredicateValue.trim();
    if (functionRef == null && filterPredicate.isBlank()) {
      throw new AppException("벌크 적용은 기존 Function을 선택하거나 Filter predicate를 입력해야 합니다.");
    }

    String currentUser = jdbcTemplate.queryForObject("SELECT USER FROM dual", String.class);
    String functionOwner;
    String packageName;
    String functionName;
    if (functionRef != null) {
      functionOwner = functionRef.owner();
      packageName = functionRef.packageName();
      functionName = functionRef.functionName();
    } else {
      functionOwner = functionOwnerValue == null || functionOwnerValue.isBlank()
          ? currentUser
          : requiredIdentifier(functionOwnerValue, "Function owner");
      packageName = null;
      functionName = functionNameValue == null || functionNameValue.isBlank()
          ? generatedFunctionName(owner + "_BULK_POLICY")
          : requiredIdentifier(functionNameValue, "Function name");
    }

    if (!filterPredicate.isBlank()) {
      if (currentUser == null || !currentUser.equalsIgnoreCase(functionOwner)) {
        throw new AppException("필터 함수 자동 생성은 현재 연결 사용자 스키마에만 가능합니다. 현재 사용자: "
            + currentUser + ", Function owner: " + functionOwner);
      }
      createFilterFunction(functionName, filterPredicate);
    }

    String statementTypes = normalizeStatementTypes(statementTypesValue);
    int created = 0;
    int skipped = 0;
    int failed = 0;
    for (VpdSchemaObjectOption target : targets) {
      String policyName = generatedPolicyName(target.objectName());
      if (mapper.findAnyPolicy(target.owner(), target.objectName(), policyName) != null) {
        skipped++;
        continue;
      }
      try {
        addPolicy(
            target.owner(),
            target.objectName(),
            policyName,
            functionOwner,
            packageName == null ? functionName : packageName + "." + functionName,
            statementTypes,
            enabled,
            updateCheck
        );
        created++;
      } catch (DataAccessException exception) {
        failed++;
      }
    }
    clearCatalogCache();
    return new VpdBulkApplyResult(targets.size(), created, skipped, failed);
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

  @Transactional
  public void createPolicy(VpdPolicyCreateCommand command) {
    String objectOwner = requiredIdentifier(command.objectOwner(), "Object owner");
    String objectName = requiredIdentifier(command.objectName(), "Object name");
    String policyName = requiredIdentifier(command.policyName(), "Policy name");
    String currentUser = jdbcTemplate.queryForObject("SELECT USER FROM dual", String.class);
    FunctionRef functionRef = parseFunctionRef(defaultFunctionKey(command.functionKey()));
    String functionOwner;
    String packageName;
    String functionName;
    if (functionRef != null) {
      functionOwner = functionRef.owner();
      packageName = functionRef.packageName();
      functionName = functionRef.functionName();
    } else {
      functionOwner = command.functionOwner() == null || command.functionOwner().isBlank()
          ? currentUser
          : requiredIdentifier(command.functionOwner(), "Function owner");
      packageName = null;
      functionName = command.functionName() == null || command.functionName().isBlank()
          ? generatedFunctionName(policyName)
          : requiredIdentifier(command.functionName(), "Function name");
    }
    String statementTypes = normalizeStatementTypes(command.statementTypes());
    String filterPredicate = command.filterPredicate() == null ? "" : command.filterPredicate().trim();

    if (!filterPredicate.isBlank()) {
      if (currentUser == null || !currentUser.equalsIgnoreCase(functionOwner)) {
        throw new AppException("필터 함수 자동 생성은 현재 연결 사용자 스키마에만 가능합니다. 현재 사용자: "
            + currentUser + ", Function owner: " + functionOwner);
      }
      createFilterFunction(functionName, filterPredicate);
    }

    addPolicy(
        objectOwner,
        objectName,
        policyName,
        functionOwner,
        packageName == null ? functionName : packageName + "." + functionName,
        statementTypes,
        command.enabled(),
        command.updateCheck()
    );
    clearCatalogCache();
  }

  public void clearCatalogCache() {
    vpdTargetsCache.clear();
    formOptionsCache = null;
  }

  private void addPolicy(
      String objectOwner,
      String objectName,
      String policyName,
      String functionOwner,
      String policyFunction,
      String statementTypes,
      boolean enabled,
      boolean updateCheck
  ) {
    jdbcTemplate.update("""
        BEGIN
          DBMS_RLS.ADD_POLICY(
            object_schema   => ?,
            object_name     => ?,
            policy_name     => ?,
            function_schema => ?,
            policy_function => ?,
            statement_types => ?,
            update_check    => %s,
            enable          => %s,
            policy_type     => DBMS_RLS.DYNAMIC
          );
        END;
        """.formatted(updateCheck ? "TRUE" : "FALSE", enabled ? "TRUE" : "FALSE"),
        objectOwner,
        objectName,
        policyName,
        functionOwner,
        policyFunction,
        statementTypes);
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

  private void createFilterFunction(String functionName, String filterPredicate) {
    jdbcTemplate.execute("""
        CREATE OR REPLACE FUNCTION %s(
          p_schema_name IN VARCHAR2,
          p_object_name IN VARCHAR2
        ) RETURN VARCHAR2
        AS
        BEGIN
          RETURN '%s';
        END;
        """.formatted(functionName, escapeSqlLiteral(filterPredicate)));
  }

  private String generatedFunctionName(String policyName) {
    String base = policyName.endsWith("_POLICY")
        ? policyName.substring(0, policyName.length() - "_POLICY".length())
        : policyName;
    String generated = base + "_FILTER";
    return generated.length() > 128 ? generated.substring(0, 128) : generated;
  }

  private String generatedPolicyName(String objectName) {
    String name = objectName + "_POLICY";
    return name.length() > 128 ? name.substring(0, 128) : name;
  }

  private FunctionRef parseFunctionRef(String functionKey) {
    if (functionKey == null || functionKey.isBlank()) {
      return null;
    }
    String[] parts = functionKey.trim().toUpperCase(Locale.ROOT).split("\\.");
    if (parts.length == 2) {
      return new FunctionRef(requiredIdentifier(parts[0], "Function owner"), null,
          requiredIdentifier(parts[1], "Function name"));
    }
    if (parts.length == 3) {
      return new FunctionRef(requiredIdentifier(parts[0], "Function owner"),
          requiredIdentifier(parts[1], "Package name"), requiredIdentifier(parts[2], "Function name"));
    }
    throw new AppException("Function 선택 값이 올바르지 않습니다: " + functionKey);
  }

  private String defaultFunctionKey(String functionKey) {
    if (functionKey != null && !functionKey.isBlank()) {
      return functionKey;
    }
    return formOptions().defaultPermissionFunctionKey();
  }

  private String normalizeStatementTypes(String value) {
    String raw = value == null || value.isBlank() ? "SELECT" : value;
    List<String> statements = List.of(raw.split(",")).stream()
        .map(String::trim)
        .filter(token -> !token.isBlank())
        .map(token -> token.toUpperCase(Locale.ROOT))
        .toList();
    if (statements.isEmpty()) {
      return "SELECT";
    }
    for (String statement : statements) {
      if (!ALLOWED_STATEMENTS.contains(statement)) {
        throw new AppException("지원하지 않는 statement type입니다: " + statement);
      }
    }
    return String.join(",", statements);
  }

  private String escapeSqlLiteral(String value) {
    return value.replace("'", "''");
  }

  private record CacheEntry<T>(T value, long expiresAt) {

    boolean expired() {
      return System.currentTimeMillis() > expiresAt;
    }
  }

  private record FunctionRef(String owner, String packageName, String functionName) {
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
