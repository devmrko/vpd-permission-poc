# 함수 설계서: `cb_agent_doc_vpd_filter` (#456)

> **부모 설계서**: ./README.md · **상태**: Approved
> **작성**: [AI] Architect · **구현**: `sql/adb/26_agent_ords_security_dynamic_vpd_filter.sql:cb_agent_doc_vpd_filter` · **테스트**: `sql/adb/27_agent_ords_security_dynamic_vpd_filter_test.sql`

## 1. 시그니처

```sql
FUNCTION cb_agent_doc_vpd_filter(
  p_schema IN VARCHAR2,
  p_object IN VARCHAR2
) RETURN VARCHAR2
```

## 2. 책임

현재 `CB_AGENT_CTX.USER_ID`와 대상 객체명에 매칭되는 저장 행규칙을 읽어 VPD WHERE predicate 문자열을 반환한다.

## 3. 입력

| 파라미터 | 타입 | 제약/검증 | 설명 |
|----------|------|-----------|------|
| `p_schema` | `VARCHAR2` | NULL이면 거부 | 대상 object owner |
| `p_object` | `VARCHAR2` | NULL이면 거부, `target_name` 비교는 대문자 | 대상 table/view name |
| `CB_AGENT_CTX.USER_ID` | context string | 숫자 변환 실패 시 거부 | 현재 bearer token 사용자 |

## 4. 출력

- **반환**: SQL predicate 문자열.
- **부수효과**: 없음. DB 조회만 수행한다.

## 5. 동작 / 알고리즘

1. `USER_ID`를 숫자로 변환한다. 실패하거나 NULL이면 `1 = 0`.
2. `p_object`를 대문자로 정규화한다.
3. 사용자 역할과 권한, 행규칙을 조회한다.
4. `ALL` 규칙이 있으면 즉시 `1 = 1`.
5. 조건형 규칙은 대상 객체에 존재하는 컬럼만 predicate에 추가한다.
6. predicate가 하나도 없으면 `1 = 0`, 있으면 OR 묶음으로 반환한다.

## 6. 에러 & 실패 모드

| 조건 | 처리 | 반환/예외 |
|------|------|-----------|
| context 없음 | 기본 거부 | `1 = 0` |
| 권한 없음 | 기본 거부 | `1 = 0` |
| 컬럼 검증 실패 | 해당 rule 무시 | 남는 rule 없으면 `1 = 0` |
| rule_value quote 포함 | literal escape | escape된 predicate |

## 7. 엣지케이스

- `MY_DEPT`의 `rule_column`이 NULL이면 `DEPT_CODE`.
- `SELF`의 `rule_column`이 NULL이면 `OWNER_EMP_NO`.
- `=`, `!=`는 `rule_column`이 반드시 필요하다.
- 여러 role/rule은 OR 조건으로 누적한다.

## 8. 복잡도 / 성능

- rule 수를 `n`이라고 할 때 O(n)로 predicate를 만든다.
- VPD policy function이므로 SELECT마다 호출될 수 있어 쿼리는 단순 join과 컬럼 existence check로 제한한다.

## 9. 의존성

- `cb_user_role`
- `cb_permission`
- `cb_permission_rule`
- `all_tab_columns`
- `CB_AGENT_CTX`

## 10. 테스트 케이스

- [ ] 정상: ALL 사용자 -> `1 = 1`.
- [ ] 정상: MY_DEPT 사용자 -> `DEPT_CODE = SYS_CONTEXT(...)`.
- [ ] 정상: SELF 사용자 -> `OWNER_EMP_NO = SYS_CONTEXT(...)`.
- [ ] 정상: `=` 규칙 -> `TO_CHAR(DOC_ID) = '1'`.
- [ ] 정상: `!=` 규칙 -> `TO_CHAR(DEPT_CODE) <> 'HR'`.
- [ ] 실패: context 없음 -> `1 = 0`.
- [ ] 실패: 존재하지 않는 컬럼 -> `1 = 0`.

## 11. 추적성

- 인수조건: #456 전체.
- 관련 ADR: 없음.
