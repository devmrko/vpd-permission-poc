# 설계서: 권한 UI와 VPD filter function의 rule type 의미 정렬 (#457)

> **상태**: Approved
> **작성**: [AI] Architect · **최종수정**: 2026-06-25
> **추적성** — Redmine: #457 · 관련 ADR: 없음
> · 구현 파일: `PermissionService`, `PermissionMapper.xml`, `permissions.html`, `app.js` · 테스트: `PermissionServiceTest`, `mvn test`

## 1. 목적 (Why)

권한 화면에서 저장하는 행규칙의 의미가 실제 `cb_agent_doc_vpd_filter`가 해석하는 predicate와 일치하도록 한다.

## 2. 범위 (Scope)

- **포함**: rule type enum 정렬, `MY_DEPT/SELF` 기본 컬럼 허용, `DEPT/EMP_NO` rule type 추가, 적용 필터 preview 개선, 권한 저장 검증 테스트 보강.
- **제외**: DB VPD 함수 자체 재설계, ORDS handler 변경, 권한 수정 전용 화면 신설.

## 3. 인수조건 (Acceptance Criteria)

- [ ] UI rule type 목록이 VPD 함수 지원 타입과 일치한다: `ALL`, `MY_DEPT`, `SELF`, `DEPT`, `EMP_NO`, `=`, `!=`.
- [ ] `MY_DEPT`는 컬럼을 비우면 `DEPT_CODE`, `SELF`는 컬럼을 비우면 `OWNER_EMP_NO`로 저장 가능하다.
- [ ] `DEPT`, `EMP_NO`, `=`, `!=`는 비교 값이 없으면 저장을 거부한다.
- [ ] 권한 목록의 적용 필터 preview가 실제 함수 의미와 같은 기본 컬럼/컨텍스트를 보여준다.
- [ ] `mvn test`가 통과한다.

## 4. 컨텍스트 & 제약

- `cb_agent_doc_vpd_filter`는 `DEPT`와 `EMP_NO`를 이미 지원한다.
- Java 서비스 검증은 저장 전 사용자 입력을 막는 1차 방어선이다.
- 실제 predicate 보안은 DB 함수에서 다시 fail-closed로 처리한다.

## 5. 아키텍처 개요

```
permissions.html rule input
  -> PermissionController.buildRules
  -> PermissionService.validateRules
  -> cb_permission_rule 저장
  -> PermissionMapper filter_preview
  -> cb_agent_doc_vpd_filter runtime predicate
```

## 6. 데이터 모델

- `rule_type`: `ALL`, `MY_DEPT`, `SELF`, `DEPT`, `EMP_NO`, `=`, `!=`.
- `rule_column`: 선택값. `MY_DEPT/SELF/DEPT/EMP_NO`는 비어 있으면 VPD 함수의 기본 컬럼을 사용한다.
- `rule_value`: `DEPT`, `EMP_NO`, `=`, `!=`에서 필수.

## 7. 함수 명세 (Function Specs)

| 함수 | 책임(1줄) | 시그니처(잠정) | 입력 | 출력 | 에러/실패 | 복잡? |
|------|-----------|----------------|------|------|-----------|-------|
| `validateRules` | 저장 전 rule type/column/value 조합 검증 | `void validateRules(long, List<RuleCommand>)` | objectId, rules | 없음 | AppException | **복잡** |
| `filter_preview SQL` | 저장된 rule을 사람이 읽는 predicate preview로 변환 | MyBatis select fragment | rule rows | text | 없음 | 단순 |
| `syncRuleTypeHints` | rule type별 UI placeholder/상태를 갱신 | JS DOM handler | rule row | DOM update | 없음 | 단순 |

## 8. 흐름 / 알고리즘

1. 사용자가 보호 객체를 선택하면 컬럼 목록을 로드한다.
2. rule type을 선택하면 UI가 기본 컬럼/비교값 필요 여부를 표시한다.
3. 저장 시 서비스가 rule type과 컬럼/값 조합을 검증한다.
4. 저장된 권한 목록에서 실제 VPD 함수 해석과 같은 preview를 보여준다.

## 9. 엣지케이스 & 에러 처리

- `ALL`은 단독만 허용한다.
- `MY_DEPT/SELF`의 컬럼 생략은 허용한다.
- 컬럼이 제공되면 보호 객체 컬럼 목록에 있어야 한다.
- 값이 필요한 타입에서 값이 없으면 저장 거부한다.

## 10. 테스트 계획

- `PermissionServiceTest`에 기본 컬럼 허용과 값 필수 검증 추가.
- `mvn test`.

## 11. 리스크 & 대안 검토

- 선택: Java 검증과 UI hint를 동시에 맞춘다. DB 함수 fail-closed만 의존하면 운영자가 저장 성공 후 조회가 안 되는 원인을 알기 어렵다.
- 대안: UI만 수정. 서버 검증과 불일치가 남아 회귀 위험이 크다.

## 12. 미해결 질문 (Open Questions)

- 권한 수정 전용 화면에서 기존 rule rows를 편집하는 UX는 별도 이슈로 분리할 수 있다.
