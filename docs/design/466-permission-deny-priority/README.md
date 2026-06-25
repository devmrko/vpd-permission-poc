# 설계서: 역할/권한 모델에 deny, 우선순위, 충돌 해소 규칙 추가 (#466)

> **상태**: Approved
> **작성**: [AI] Architect · **최종수정**: 2026-06-25
> **추적성** — Redmine: #466 · 관련 ADR: 없음
> · 구현 파일: `cb_permission.permission_effect`, `PermissionService`, `PermissionMapper`, `cb_agent_doc_vpd_filter` · 테스트: SQLcl `27`, `mvn test`

## 1. 목적

역할이 여러 개 붙은 사용자에게 allow 권한과 deny 권한이 동시에 존재할 때 최종 접근 범위를 명확히 계산한다.

## 2. 범위

- 포함: permission 단위 `ALLOW/DENY` 효과 추가, deny 우선 VPD predicate 생성, UI 저장/목록 표시, 테스트.
- 제외: role 자체 우선순위 숫자, 승인 workflow, rule별 deny.

## 3. 인수조건

- [ ] `cb_permission.permission_effect`가 `ALLOW` 기본값으로 추가된다.
- [ ] deny permission은 같은 object/action의 allow 결과에서 제외된다.
- [ ] deny `ALL`이 있으면 최종 결과는 `1 = 0`이다.
- [ ] allow가 없으면 deny가 있어도 `1 = 0`이다.
- [ ] UI에서 권한 효과를 선택하고 목록에서 확인할 수 있다.

## 4. 설계 결정

- 효과는 `cb_permission` 단위로 둔다. 하나의 permission set이 allow인지 deny인지 명확히 구분한다.
- 최종 predicate는 `(<allow predicate>) AND NOT (<deny predicate>)`이다.
- allow `ALL`은 `1 = 1`, deny `ALL`은 즉시 `1 = 0`이다.
- deny만 있고 allow가 없으면 whitelist 기본값에 따라 `1 = 0`이다.
