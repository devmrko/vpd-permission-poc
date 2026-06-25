package com.cloudhandson.vpdbackoffice.domain.operation;

public record OperationStatusRow(
    long objectId,
    String owner,
    String objectName,
    String ordsPath,
    String enabledYn,
    Integer permissionCount,
    Integer ruleCount,
    String policyNames,
    String policyEnabled,
    String functionName,
    String functionStatus,
    Long handlerId,
    String handlerMethod,
    String handlerSourceType,
    String handlerFullPath,
    String lastProbeStatus,
    Integer lastProbeRowCount,
    String lastProbeErrorCode,
    String lastProbeAt
) {

  public String displayName() {
    return owner + "." + objectName;
  }

  public String healthLevel() {
    if (functionStatus != null && !"VALID".equalsIgnoreCase(functionStatus)) {
      return "ERROR";
    }
    if (handlerId == null || policyNames == null || policyNames.isBlank()
        || policyEnabled == null || !policyEnabled.toUpperCase().contains("YES")) {
      return "WARN";
    }
    if (lastProbeStatus != null && !lastProbeStatus.isBlank()
        && !lastProbeStatus.toUpperCase().contains("SUCCESS")
        && !lastProbeStatus.toUpperCase().contains("ALLOW")) {
      return "WARN";
    }
    return "OK";
  }

  public String actionText() {
    if (handlerId == null) {
      return "ORDS path와 handler schema/module/template 매핑을 확인하세요.";
    }
    if (policyNames == null || policyNames.isBlank()) {
      return "VPD policy를 적용하세요.";
    }
    if (policyEnabled == null || !policyEnabled.toUpperCase().contains("YES")) {
      return "VPD policy enable 상태를 확인하세요.";
    }
    if (functionStatus != null && !"VALID".equalsIgnoreCase(functionStatus)) {
      return "Policy function 컴파일 오류를 확인하세요.";
    }
    if (lastProbeStatus == null || lastProbeStatus.isBlank()) {
      return "ORDS 검증을 한 번 실행하세요.";
    }
    return "현재 상태에서 즉시 조치할 항목은 없습니다.";
  }

  public String permissionSummary() {
    int permissions = permissionCount == null ? 0 : permissionCount;
    int rules = ruleCount == null ? 0 : ruleCount;
    return permissions + " permissions / " + rules + " rules";
  }
}
