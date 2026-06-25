package com.cloudhandson.vpdbackoffice.domain.vpd;

public record VpdBulkApplyResult(
    int total,
    int created,
    int skipped,
    int failed
) {

  public String summary() {
    return "VPD bulk 적용 완료: 대상 " + total + "개, 등록 " + created
        + "개, 건너뜀 " + skipped + "개, 실패 " + failed + "개";
  }
}
