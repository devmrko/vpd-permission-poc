# 설계서: VPD predicate 생성 함수 구조화 및 단위 검증 보강 (#456)

> **상태**: Approved
> **작성**: [AI] Architect · **최종수정**: 2026-06-25
> **추적성** — Redmine: #456 · 관련 ADR: 없음
> · 구현 파일: `sql/adb/26_agent_ords_security_dynamic_vpd_filter.sql`, `sql/adb/27_agent_ords_security_dynamic_vpd_filter_test.sql` · 테스트: `sql/adb/27_agent_ords_security_dynamic_vpd_filter_test.sql`, `mvn test`

## 1. 목적 (Why)

`cb_agent_doc_vpd_filter`가 DB에 저장된 행규칙을 기반으로 whitelist predicate를 만드는지, rule type별 경계 조건이 fail-closed로 동작하는지 재실행 가능한 테스트로 고정한다.

## 2. 범위 (Scope)

- **포함**: VPD predicate 생성 함수의 의도 주석 보강, rule type별 predicate 단위 검증 SQL 추가, 임시 테스트 데이터 생성/정리.
- **제외 (out of scope)**: 백오피스 화면 변경, ORDS handler 자동 생성 변경, MCP/LLM 응답 개선.

## 3. 인수조건 (Acceptance Criteria)

- [ ] `ALL`, `MY_DEPT`, `SELF`, `=`, `!=` 규칙이 기대 predicate를 생성한다.
- [ ] 권한이 없거나 `USER_ID` 컨텍스트가 없으면 `1 = 0`을 반환한다.
- [ ] 대상 객체에 없는 `rule_column`은 권한 우회로 이어지지 않고 `1 = 0`으로 닫힌다.
- [ ] 테스트 스크립트는 임시 role/permission/rule을 만들고 종료 시 정리한다.
- [ ] 기존 ORDS 검증 결과와 `mvn test`가 유지된다.

## 4. 컨텍스트 & 제약

- 의존성: Oracle ADB, `CB_AGENT_CTX`, `cb_user_role`, `cb_permission`, `cb_permission_rule`, `all_tab_columns`.
- 제약: VPD policy function은 SQL predicate 문자열을 반환하므로 문자열 escape와 컬럼명 검증이 필수다.
- 가정: `p_object`는 DBMS_RLS가 전달하는 실제 object name이고, `cb_permission.target_name`은 대문자 object name으로 저장된다.

## 5. 아키텍처 개요

- `26_agent_ords_security_dynamic_vpd_filter.sql`: 운영 DB에 적용할 predicate 생성 함수.
- `27_agent_ords_security_dynamic_vpd_filter_test.sql`: SQLcl로 실행하는 단위 검증 스크립트.

```
CB_AGENT_CTX.USER_ID
  -> CB_USER_ROLE
  -> CB_PERMISSION(target_name = p_object, action_name = SELECT)
  -> CB_PERMISSION_RULE
  -> cb_agent_doc_vpd_filter returns SQL predicate
```

I/O와 순수 로직 경계:
- I/O: rule 조회, `all_tab_columns` 컬럼 검증.
- 전략 로직: rule type별 predicate 문자열 생성, 기본 거부 처리.

## 6. 데이터 모델

- 입력: `p_schema VARCHAR2`, `p_object VARCHAR2`, `SYS_CONTEXT('CB_AGENT_CTX','USER_ID')`.
- 저장 규칙: `rule_column`, `rule_type`, `rule_value`.
- 출력: VPD predicate `VARCHAR2`, 예: `1 = 0`, `1 = 1`, `(DEPT_CODE = SYS_CONTEXT(...))`.

## 7. 함수 명세 (Function Specs)

| 함수 | 책임(1줄) | 시그니처(잠정) | 입력 | 출력 | 에러/실패 | 복잡? |
|------|-----------|----------------|------|------|-----------|-------|
| `cb_agent_doc_vpd_filter` | 현재 사용자와 대상 객체의 저장 규칙을 SQL predicate로 변환 | `FUNCTION(p_schema VARCHAR2, p_object VARCHAR2) RETURN VARCHAR2` | schema, object, app context | predicate string | 컨텍스트/권한 없음 `1 = 0` | **복잡** |
| `assert_predicate_contains` | predicate 결과가 기대 문자열을 포함하는지 검증 | PL/SQL local procedure | label, user, object, expected | 없음 | 실패 시 raise_application_error | 단순 |
| `assert_predicate_equals` | predicate 결과가 기대 문자열과 같은지 검증 | PL/SQL local procedure | label, user, object, expected | 없음 | 실패 시 raise_application_error | 단순 |

## 8. 흐름 / 알고리즘

1. 테스트용 role/permission/rule을 생성한다.
2. 사용자별 `cb_agent_ctx_pkg.set_user`로 context를 설정한다.
3. `cb_agent_doc_vpd_filter('ADMIN', object)`를 직접 호출한다.
4. 반환 predicate가 기대 조건을 포함하거나 일치하는지 검증한다.
5. 잘못된 컬럼 규칙은 `1 = 0`을 반환하는지 확인한다.
6. 테스트 데이터를 정리하고 context를 clear한다.

## 9. 엣지케이스 & 에러 처리

- `USER_ID` 없음: `1 = 0`.
- 권한 없음: `1 = 0`.
- rule_column 없음: `MY_DEPT`는 `DEPT_CODE`, `SELF`는 `OWNER_EMP_NO`를 기본 컬럼으로 사용.
- 존재하지 않는 rule_column: 해당 rule 무시, 남는 rule 없으면 `1 = 0`.
- quote 포함 rule_value: single quote escape가 적용된 literal predicate 생성.

## 10. 테스트 계획

- SQLcl: `@sql/adb/26_agent_ords_security_dynamic_vpd_filter.sql`
- SQLcl: `@sql/adb/27_agent_ords_security_dynamic_vpd_filter_test.sql`
- Maven: `mvn test`
- ORDS smoke: 기존 HR=3, SELF=1, ALL=6 결과 유지 확인.

## 11. 리스크 & 대안 검토

- 선택: PL/SQL 함수 직접 호출 기반 단위 검증. VPD owner bypass 문제와 무관하게 predicate 자체를 빠르게 검증할 수 있다.
- 대안: ORDS만으로 검증. end-to-end는 좋지만 실패 원인이 handler/token/VPD 중 어디인지 분리하기 어렵다.

## 12. 미해결 질문 (Open Questions)

- rule type 확장 시 UI enum과 PL/SQL branch를 어떻게 한 곳에서 관리할지 별도 이슈 #457에서 다룬다.
