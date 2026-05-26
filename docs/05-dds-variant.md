# 05 · DDS Variant — 같은 데모를 Oracle Deep Data Security 로

> 본 문서는 **선택사항** 입니다. 메인 데모(`run.sh all`) 는 전통적 **VPD** 경로로
> 그대로 동작합니다. 이 문서는 동일한 4-user 매트릭스를 Oracle AI Database
> **26ai 신기능 Deep Data Security (DDS)** 로 재구현한 `sql/adb/13_dds_variant.sql`
> 의 사용법과, VPD ↔ DDS 1:1 매핑을 다룹니다.
>
> 본 변형은 **2026-05-26 실제 ADB (23.26.2.2.0) 에서 E2E 검증** 됨 (8장 결과).

---

## 1. Deep Data Security 는 무엇을 할 수 있는가

Oracle 이 **2026-04-09** 에 VPD/RAS 의 공식 후계자로 발표한 native authorization
시스템 (Oracle AI Database 26ai). 공식 한 줄:

> "Extends and modernizes Oracle Virtual Private Database and Real Application
> Security, moving from procedural PL/SQL and API-based controls **to
> declarative policies in SQL**."

### 1.1 핵심 기능 분류

| 영역 | DDS 가 제공하는 것 | 어떤 객체로 표현 |
|---|---|---|
| **인증 / 식별** | 전통적 DB 사용자와 분리된 schemaless "**End User**" (스키마/객체 없음, 로그인만) | `CREATE END USER` |
| **권한 묶음** | 일반 ROLE 과 별도 namespace 의 "**Data Role**" (외부 IdP claim 매핑 가능) | `CREATE DATA ROLE [MAPPED TO 'AZURE_ROLE=manager']` |
| **세분화된 데이터 접근** | row + column + cell 단위 access control 을 **DDL 한 줄** 로 | `CREATE DATA GRANT ... AS SELECT (cols) ON tbl WHERE ...` |
| **연산 단위 권한** | SELECT 외에도 UPDATE / INSERT / DELETE 를 컬럼별 세분화 | `AS SELECT (a,b), UPDATE (salary)` |
| **컬럼 동적 마스킹** | 허용되지 않은 컬럼은 **NULL** 로 자동 반환 (별도 Redaction 정책 불필요) | `AS SELECT (ALL COLUMNS EXCEPT ssn)` |
| **Mandatory Access Control** | 기존 GRANT / SELECT ANY TABLE / DBA 권한도 우회 못 함 — 데이터 grant 가 단일 권한 출처 | `SET USE DATA GRANTS ONLY ON tbl ENABLED` |
| **세션 식별자 전파** | LOGON 트리거 없이 자동. OAuth2 token 의 claim 까지 in-memory JSON context 로 보관 | `ORA_END_USER_CONTEXT.username` / `.<claim>` |
| **OAuth2 federated identity** | OCI IAM, Microsoft Entra ID 의 토큰을 DB 가 직접 검증하고 claim 활용 | OCI IAM / Entra ID OIDC 연동 |
| **Application identity** | 사람이 아닌 application (ORDS / agentic AI / micro-service) 도 1급 시민으로 인증 | `CREATE APPLICATION` + JWT-bearer |
| **End-User Context Object** | 데이터 grant 안에서 참조 가능한 임의 JSON 컨텍스트를 IdP claim 매핑으로 정의 | `CREATE END USER CONTEXT ... USING JSON SCHEMA` |
| **객체 hiding** | 권한이 없으면 "**0 rows**" 가 아니라 `ORA-00942 (table/view does not exist)` — 객체 자체가 안 보임 | DATA GRANT 부재 시 기본 동작 |
| **권한 점검 함수** | 정책 안 또는 응용 PL/SQL 에서 "이 사용자가 이 컬럼/operation 권한 있나?" 동적 체크 | `ORA_IS_COLUMN_AUTHORIZED()`, `ORA_CHECK_DATA_PRIVILEGE()` |
| **감사** | grant / role / end-user 일체가 dictionary view 로 노출 | `DBA_DATA_GRANTS`, `DBA_DATA_ROLES`, `DBA_END_USERS` |

### 1.2 VPD/RAS 대비 무엇이 달라졌나

| 측면 | VPD/RAS (procedural) | DDS (declarative) |
|---|---|---|
| 정책 작성 | PL/SQL **함수** 가 `WHERE` 문자열을 빌드해서 반환 | **DDL 한 줄** (`CREATE DATA GRANT ... WHERE ...`) |
| 식별자 전달 | LOGON 트리거 + `DBMS_SESSION.SET_CONTEXT` + 직접 만든 namespace | 세션 시작 시 자동, `ORA_END_USER_CONTEXT.username` |
| 외부 IdP 연동 | 별도 CMU + 트리거 핸들링 필요 | OCI IAM / Entra ID OAuth2 **native** 검증 |
| 권한 모델 | DAC (DBA 가 GRANT 으로 우회 가능) | MAC option 으로 GRANT 도 무시 가능 |
| 컬럼 마스킹 | **별도** Data Redaction 정책 필요 | grant 안에 `ALL COLUMNS EXCEPT` 로 통합 |
| 행 + 컬럼 + 연산 | 따로 따로 설정 | 한 statement 안에 통합 |
| 인증 단위 | 풀 데이터베이스 사용자 (스키마 소유) | schemaless END USER (스키마 없음, 로그인만) |
| 디버깅 | 정책 함수가 빌드한 동적 SQL 추적 | grant 의 WHERE 절이 그대로 plan 에 보임 |

### 1.3 어떤 시나리오에 가장 유용한가

* **Multi-tenant SaaS** — 단일 테이블/뷰에 수십~수백 tenant 의 데이터, 각 사용자는 자기 tenant 만. DDS 의 `WHERE tenant_id = ORA_END_USER_CONTEXT.tenant_id` 한 줄.
* **Agentic AI / LLM-driven 쿼리** — 에이전트(ORDS/MCP 서버)가 **사용자 대신 쿼리** 해도 사용자 권한 모델이 유지되어야 함. End User identity 가 application 토큰과 분리되어 흐름.
* **HR / 의료 / 금융** — row 단위 (담당자 = 본인 record) + column 단위 (SSN/salary 마스킹) + operation 단위 (manager 만 UPDATE salary) 가 동시에 필요한 경우.
* **Federated identity** — Entra ID / OCI IAM 의 group/role 을 DB 권한에 직접 매핑 (`CREATE DATA ROLE x MAPPED TO 'AZURE_ROLE=hr_manager'`). 별도 sync 잡 불필요.
* **Compliance** (HIPAA / PCI / GDPR) — MAC 모드에서는 DBA 도 grant 가 없으면 데이터를 못 본다. Audit 도 declarative grant 단위로 명확.

### 1.4 무엇은 못/안 하는가

* 본질적으로 **Oracle DB 안의 데이터** 에 대한 정책. 원격 DB 데이터에 적용하려면 본 POC 처럼 **로컬 뷰** 를 거쳐야 함 (VPD 와 동일한 제약).
* 동적 외부 호출 (예: 외부 ACL 서버 콜) 은 grant WHERE 절로 표현하기 어려움 — 그런 케이스는 여전히 application-tier 권한이 필요.
* MAC (`USE DATA GRANTS ONLY`) 를 켜면 기존 application 의 normal GRANT 가 무력화되어, 마이그레이션 시 한꺼번에 전환해야 함.
* 26ai (23.26.2+) 미만에서는 동작 안 함. ADB 의 경우 자동 패치 채널이지만 on-prem 은 명시적 업그레이드 필요.

---

## 2. 전제 조건

| 항목 | 요건 |
|---|---|
| Oracle 버전 | AI Database **23.26.2 이상** (`SELECT banner_full FROM v$version`) |
| 초기화 파라미터 | `COMPATIBLE >= 20.0` |
| 관리자 권한 | `CREATE END USER`, `CREATE DATA ROLE`, `GRANT DATA ROLE`, `CREATE DATA GRANT` (ADB ADMIN 은 기본 보유) |
| Dictionary view | `DBA_END_USERS`, `DBA_DATA_ROLES`, `DBA_DATA_GRANTS` 존재 여부로 가용성 확인 |

> ADB 가 아직 26ai 로 업그레이드되지 않은 환경이라면 `CREATE END USER` 부터
> `ORA-00942` 류로 실패합니다. 본 디렉토리의 VPD 경로는 그대로 동작합니다.

---

## 3. 실행

```bash
source .env                              # DDSUSER_*_PASSWORD 로드
sqlplus "$ADB_USER/$ADB_PASSWORD@$ADB_TNS" @sql/adb/13_dds_variant.sql
```

스크립트가 만드는 객체:

```
VIEW       v_dds_customers_pg   v_dds_customers_my   (VPD policy 없음 — DDS-only)
END USER   ddsuser_my   ddsuser_pg   ddsuser_both   ddsuser_none
ROLE       dds_db_role                        (CREATE SESSION 보유)
DATA ROLE  my_only_role   pg_only_role   both_sources_role   connect_only_role
DATA GRANT
  dds_my_only_grant_mysql   -> v_dds_customers_my  TO my_only_role
  dds_pg_only_grant_pg      -> v_dds_customers_pg  TO pg_only_role
  dds_both_grant_pg         -> v_dds_customers_pg  TO both_sources_role
  dds_both_grant_mysql      -> v_dds_customers_my  TO both_sources_role
```

> **왜 별도 뷰?** 메인 데모의 `v_customers_pg/my` 에는 VPD policy 가 살아
> 있어 `ddsuser_*` 세션에서는 정책 함수가 `1=0` 을 반환합니다. DDS 가
> "허용"해도 VPD 가 "0 rows" 로 깎기 때문에 의도한 결과가 안 나옴.
> 그래서 DDS 전용 뷰 `v_dds_customers_*` 를 따로 만들어 grant 의 단일
> 권한 출처로 만듭니다.

검증:

```bash
sqlplus '"ddsuser_pg"'/"$DDSUSER_PG_PASSWORD"@"$ADB_TNS" <<'EOF'
SELECT ORA_END_USER_CONTEXT.username FROM dual;
SELECT COUNT(*) FROM admin.v_dds_customers_pg;   -- 12 (기대)
SELECT COUNT(*) FROM admin.v_dds_customers_my;   -- ORA-00942 (기대)
EOF
```

---

## 4. VPD ↔ DDS 1:1 매핑

| 관심사 | VPD 경로 (이 POC 본체) | DDS 경로 (`13_dds_variant.sql`) |
|---|---|---|
| 사용자 생성 | `CREATE USER vpduser_my IDENTIFIED BY ...` | `CREATE END USER "ddsuser_my" IDENTIFIED BY ...` |
| 사용자 = 스키마? | 예 (Oracle 의 전통적 모델) | **아니오** — END USER 는 스키마/객체 없음 |
| 그룹/역할 매핑 | `app_group` + `user_group` 매핑 테이블 | `CREATE DATA ROLE` + `GRANT DATA ROLE x TO "user"` |
| 정책 표현 | `dbms_rls.add_policy` + PL/SQL 함수 `vpd_region_filter` | `CREATE DATA GRANT ... AS SELECT ON tbl WHERE ... TO role` |
| 식별자 전파 | LOGON 트리거 → `DBMS_SESSION.SET_CONTEXT('VPD_CTX', ...)` | 세션 자동: `ORA_END_USER_CONTEXT.username` |
| 행 필터 | 정책 함수가 `WHERE region IN (...)` 반환 | `CREATE DATA GRANT ... WHERE region IN ('APAC','EMEA')` |
| 컬럼 마스킹 | 별도 `DBMS_REDACT.ADD_POLICY` (06a_redaction.sql) | 같은 grant 안에 `AS SELECT (ALL COLUMNS EXCEPT email)` |
| 권한 부여 단위 | `permission` 테이블 INSERT | DDL 한 줄 (`CREATE DATA GRANT`) |
| 우회 차단 | GRANT 안 주기 + EXEMPT ACCESS POLICY 안 주기 + DBMS_RLS 안 주기 | `SET USE DATA GRANTS ONLY ON tbl ENABLED` (MAC) 한 줄 |
| 미허용 객체 응답 | 0 rows (silent) | `ORA-00942` (객체 자체 hiding) |

---

## 5. 행/컬럼 변형 — 한 줄 비교

**행 필터 (region='APAC' 만):**

```sql
-- VPD: 정책 함수 안에서 IN-list 빌드 + permission 테이블 UPDATE
UPDATE permission SET allowed_regions = 'APAC' WHERE group_id = 30 ...;

-- DDS: grant 한 줄
CREATE OR REPLACE DATA GRANT admin.dds_both_grant_pg
  AS SELECT
  ON admin.v_dds_customers_pg
  WHERE region = 'APAC'
  TO both_sources_role;
```

**컬럼 마스킹 (email 을 NULL 로):**

```sql
-- VPD: 별도 Redaction 정책
DBMS_REDACT.ADD_POLICY(object_schema=>'ADMIN', object_name=>'V_CUSTOMERS_PG',
  column_name=>'EMAIL', function_type=>DBMS_REDACT.NULLIFY, ...);

-- DDS: 같은 grant 안에서
CREATE OR REPLACE DATA GRANT admin.dds_both_grant_pg
  AS SELECT (ALL COLUMNS EXCEPT email)
  ON admin.v_dds_customers_pg
  TO both_sources_role;
```

**Operation 별 세분화 (manager 만 salary 업데이트):**

```sql
-- VPD/RAS 로는 별도 정책 필요. DDS 는 한 grant:
CREATE DATA GRANT admin.manager_compensation_grant
  AS SELECT (ALL COLUMNS EXCEPT ssn), UPDATE (salary)
  ON admin.employees
  WHERE manager = ORA_END_USER_CONTEXT.username
  TO manager_role;
```

---

## 6. 한계와 주의

* DDS Data Grant 는 **테이블·뷰** 모두에 적용 가능. 본 POC 의 `v_dds_customers_*`
  뷰는 DB Link 패스스루이므로 grant 가 뷰 단에서 평가됩니다. 원격 push-down 은
  여전히 ADB → 원격 옵티마이저 협상에 의존합니다 (VPD 와 동일한 성능 특성).
* 본 스크립트는 의도적으로 `USE DATA GRANTS ONLY` (MAC) 를 **켜지 않습니다**.
  운영에서는 MAC 활성화가 권장 (단일 권한 출처).
* OAuth2 identity propagation (OCI IAM / Microsoft Entra ID claim) 은 본
  문서에선 다루지 않습니다. 직접 로그온 시나리오만 다룹니다. agentic AI / ORDS
  중간 계층에서 사용자 ID 를 전파하는 경우 공식 가이드 (`G50191`) 의 4장
  (OCI IAM) / 5장 (Entra ID) 참고.

---

## 7. 정리/삭제

```sql
-- DDS 변형만 정리 (VPD 데모는 그대로 유지)
DROP DATA GRANT admin.dds_my_only_grant_mysql;
DROP DATA GRANT admin.dds_pg_only_grant_pg;
DROP DATA GRANT admin.dds_both_grant_pg;
DROP DATA GRANT admin.dds_both_grant_mysql;
DROP DATA ROLE my_only_role;
DROP DATA ROLE pg_only_role;
DROP DATA ROLE both_sources_role;
DROP DATA ROLE connect_only_role;
DROP ROLE dds_db_role;
DROP END USER "ddsuser_my";
DROP END USER "ddsuser_pg";
DROP END USER "ddsuser_both";
DROP END USER "ddsuser_none";
DROP VIEW v_dds_customers_pg;
DROP VIEW v_dds_customers_my;
```

---

## 8. E2E 검증 결과 (2026-05-26, ADB 23.26.2.2.0)

`sql/adb/13_dds_variant.sql` 실행 후 4명의 ddsuser 로 `v_dds_customers_*` 조회:

| 사용자 | `v_dds_customers_pg` | `v_dds_customers_my` | 판정 |
|---|---|---|---|
| `ddsuser_my`   | `ORA-00942` (hidden) | **17 rows**          | ✓ |
| `ddsuser_pg`   | **12 rows**          | `ORA-00942` (hidden) | ✓ |
| `ddsuser_both` | **12 rows**          | **17 rows**          | ✓ |
| `ddsuser_none` | `ORA-00942` (hidden) | `ORA-00942` (hidden) | ✓ |

**관찰:** VPD 가 "0 rows" 로 조용히 깎는 자리에 DDS 는 **`ORA-00942 (table or
view does not exist)`** — 객체 자체가 안 보입니다. enumeration 측면에서 더
강한 hiding (공격자가 "객체는 있지만 막혀있다" 는 사실 자체를 알 수 없음).
