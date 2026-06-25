# SQLcl VPD 배포/검증/롤백 런북 (#460)

## 사전 점검

```bash
SQLCL_BIN=/path/to/sql \
SQLCL_JAVA_HOME=/path/to/jdk \
./run.sh backoffice-env-check
```

확인 항목:

- SQLcl 버전이 출력된다.
- Java 11 이상이 출력된다.
- DB 현재 사용자가 의도한 schema다. 예: `ADMIN`.
- `CB_AGENT_DOC_VPD_FILTER` 상태가 `VALID`다.
- ORDS endpoint가 200/401/403 중 하나를 반환한다. 404면 base URL 또는 path가 맞지 않는다.

## VPD filter 배포

```bash
SQLCL_BIN=/path/to/sql SQLCL_JAVA_HOME=/path/to/jdk "$SQLCL_BIN" "USER/PASSWORD@TNS"
```

SQLcl 안에서 실행:

```sql
@sql/adb/26_agent_ords_security_dynamic_vpd_filter.sql
SHOW ERRORS FUNCTION cb_agent_doc_vpd_filter
@sql/adb/27_agent_ords_security_dynamic_vpd_filter_test.sql
```

기대 결과:

- function compile 성공
- `SHOW ERRORS` 결과 없음
- `Dynamic VPD filter unit checks passed`

## ORDS 회귀 검증

```bash
SQLCL_BIN=/path/to/sql \
SQLCL_JAVA_HOME=/path/to/jdk \
./run.sh backoffice-vpd-ords-test
```

기대 결과:

- HR: 3행, `DOC_ID=[1,2,6]`, `CONTENTS` 미노출
- SELF: 1행, `DOC_ID=[3]`, `CONTENTS` 미노출
- ALL: 6행, `DOC_ID=[1,2,3,4,5,6]`, `CONTENTS` 노출
- INVALID_TOKEN: HTTP 403 등 성공이 아닌 응답

## 장애 시 확인 순서

1. `./run.sh backoffice-env-check`로 SQLcl/JDK/DB/ORDS 상태를 먼저 확인한다.
2. DB function이 `INVALID`면 `SHOW ERRORS FUNCTION cb_agent_doc_vpd_filter`를 본다.
3. ORDS가 404면 `BACKOFFICE_ORDS_BASE_URL`과 `cb_protected_object.ords_path`를 비교한다.
4. ORDS가 403이면 token hash, 만료시각, revoke 여부를 확인한다.
5. 행 수가 맞지 않으면 `cb_user_role`, `cb_permission`, `cb_permission_rule`을 확인한다.

## 롤백

가장 단순한 롤백은 직전 Git revision의 `26_agent_ords_security_dynamic_vpd_filter.sql`을 다시 적용하는 것이다.

```bash
git show HEAD~1:sql/adb/26_agent_ords_security_dynamic_vpd_filter.sql > /tmp/previous-vpd-filter.sql
SQLCL_BIN=/path/to/sql SQLCL_JAVA_HOME=/path/to/jdk "$SQLCL_BIN" "USER/PASSWORD@TNS"
```

SQLcl 안에서:

```sql
@/tmp/previous-vpd-filter.sql
SHOW ERRORS FUNCTION cb_agent_doc_vpd_filter
```

롤백 후에도 반드시 ORDS 회귀 검증을 다시 실행한다.
