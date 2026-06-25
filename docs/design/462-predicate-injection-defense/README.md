# 설계서: Predicate injection 및 권한 우회 방어 테스트 (#462)

> **상태**: Approved
> **작성**: [AI] Architect · **최종수정**: 2026-06-25
> **추적성** — Redmine: #462 · 관련 ADR: 없음
> · 구현 파일: `sql/adb/27_agent_ords_security_dynamic_vpd_filter_test.sql` · 테스트: SQLcl `27`, `./run.sh backoffice-vpd-ords-test`, `mvn test`

## 1. 목적 (Why)

VPD predicate function이 DB에 저장된 `rule_column`/`rule_value`를 SQL 문자열로 변환하므로, quote/OR/주석/함수형 컬럼명 입력이 권한 우회로 이어지지 않는지 테스트로 고정한다.

## 2. 범위 (Scope)

- **포함**: `rule_value` injection 문자열 escape 검증, `rule_column` injection fail-closed 검증, target mismatch 기본 거부 검증.
- **제외**: UI 입력 fuzzing, ORDS response SQL 노출 정책 변경.

## 3. 인수조건 (Acceptance Criteria)

- [ ] `rule_value`에 single quote가 있어도 literal escape가 적용된다.
- [ ] `rule_value`에 `OR 1=1` 또는 `--`가 있어도 predicate 구조를 탈출하지 않는다.
- [ ] `rule_column`에 함수 호출/표현식이 들어가면 해당 rule은 무시되고 `1 = 0`이 된다.
- [ ] `p_object`가 권한의 `target_name`과 다르면 `1 = 0`이 된다.
- [ ] 기존 정상/경계 테스트와 ORDS 회귀 테스트가 유지된다.

## 4. 컨텍스트 & 제약

- `safe_column`은 `DBMS_ASSERT.SIMPLE_SQL_NAME`과 `all_tab_columns` 존재 확인을 사용한다.
- `quote_literal`은 single quote를 두 번으로 escape한다.
- 테스트 데이터는 `456000~456999` 범위를 재사용한다.

## 5. 아키텍처 개요

```
malicious cb_permission_rule rows
  -> cb_agent_doc_vpd_filter('ADMIN', 'CB_V_SEARCH_DOCUMENTS')
  -> assert predicate contains escaped literal or equals 1 = 0
```

## 6. 데이터 모델

- injection users:
  - `456107`: quote 포함 값
  - `456108`: `OR 1=1 --` 포함 값
  - `456109`: 함수형 컬럼명
  - `456110`: target mismatch 확인

## 7. 함수 명세 (Function Specs)

| 함수 | 책임(1줄) | 시그니처(잠정) | 입력 | 출력 | 에러/실패 | 복잡? |
|------|-----------|----------------|------|------|-----------|-------|
| `assert_contains_for_object` | 특정 object에 대한 predicate 포함 검증 | PL/SQL local procedure | label, user, object, expected | 없음 | raise_application_error | 단순 |
| `assert_equals_for_object` | 특정 object에 대한 predicate 일치 검증 | PL/SQL local procedure | label, user, object, expected | 없음 | raise_application_error | 단순 |

## 8. 흐름 / 알고리즘

1. 악성 문자열이 들어간 test user/role/permission/rule을 생성한다.
2. predicate function을 직접 호출한다.
3. 값 기반 injection은 quote escaped literal로 남는지 확인한다.
4. 컬럼 기반 injection은 `1 = 0`이 되는지 확인한다.
5. target mismatch는 `1 = 0`인지 확인한다.

## 9. 엣지케이스 & 에러 처리

- `TITLE = 'HR' OR '1'='1'` 형태 문자열은 전체가 literal 값이어야 한다.
- `DOC_ID) OR 1=1 --` 같은 컬럼명은 컬럼 검증 실패로 무시되어야 한다.
- 모든 rule이 무시되면 whitelist 기본값 `1 = 0`이어야 한다.

## 10. 테스트 계획

- `@sql/adb/26_agent_ords_security_dynamic_vpd_filter.sql`
- `@sql/adb/27_agent_ords_security_dynamic_vpd_filter_test.sql`
- `./run.sh backoffice-vpd-ords-test`
- `mvn test`

## 11. 리스크 & 대안 검토

- 선택: DB 함수 직접 테스트. 공격 문자열이 실제 predicate 문자열로 어떻게 변환되는지 가장 직접적으로 확인할 수 있다.
- 대안: ORDS만 테스트. 결과 행 수는 확인 가능하지만 predicate escaping 자체를 확인하기 어렵다.

## 12. 미해결 질문 (Open Questions)

- 더 넓은 fuzzing은 별도 보안 테스트 체계가 필요할 때 확장한다.
