# 2026-06-08 — DDS 깊이 이해 Q&A 세션 기록

이 문서는 vpd-permission-poc 작업 중 DDS (Oracle 26ai Deep Data Security) 의
운영 모델·시나리오·확장 가능성을 짚어본 Q&A 세션의 기록이다. 정식 docs 가 아니라
개인 참조용 노트.

---

## TL;DR — 핵심 7 가지

1. **DDS 의 권한 모델 = 3 객체**: `END USER` (개인) → `DATA ROLE` (그룹) → `DATA GRANT` (정책).
2. **DATA GRANT 한 줄**에 행 필터(WHERE) + 컬럼 마스킹(`ALL COLUMNS EXCEPT`) + 작업 단위(SELECT/INSERT/UPDATE) 가 다 들어간다.
3. **END USER ≠ 그룹**. 그룹은 `DATA ROLE`. 같은 권한 받을 사람들을 묶고, DATA GRANT 는 항상 DATA ROLE 대상.
4. **테이블 DDL 은 안 건드린다**. 정책은 별도 DDL 객체(`CREATE DATA GRANT ...`) 로 외부에서 붙는다. 변경/회수는 객체 단위 (`CREATE OR REPLACE`, `DROP`).
5. **DB Link / Data Catalog 등 외부 데이터도 통제 가능**. 패턴은 동일: `원격객체 → 로컬 VIEW → DATA GRANT`. 원격 객체에 직접은 못 붙임.
6. **권한 없는 유저** → `ORA-00942` (객체 자체 미노출). VPD 의 "0 rows" 보다 강한 enumeration 방어.
7. **권한 매핑 테이블 모델은 VPD 가 맞음**. DDS 는 권한을 "데이터 행" 이 아니라 "DDL 객체" 로 두는 모델.

---

## 이번 세션에서 README 에 반영된 변경

| 커밋 | 내용 |
|---|---|
| `3045271` | README 에 `## 무엇을 통제하는가` 섹션 추가 — 행/컬럼/구현 비교 (드라이 톤) |
| `b80d119` | README 에 `## DDS 설정 핵심` 추가 — 사전조건·4스텝 DDL·함정 4종·변주 |

두 섹션 모두 비유 없이 표·코드 위주.

---

## 1. DDS 기본 모델

### 등장 인물 3 종

| | 역할 | 한 줄 |
|---|---|---|
| **END USER** | 데이터 조회자 (개인) | 스키마 없음 → 객체 못 만듦 |
| **DATA ROLE** | 권한 묶음 (그룹) | END USER 는 이걸 통해서만 권한 받음 |
| **DATA GRANT** | "누가 뭘 본다" 선언 | DDL 한 줄에 행·컬럼·작업 다 들어감 |

흐름: `END USER → DATA ROLE → DATA GRANT → 테이블/뷰`

### DATA GRANT 한 줄 예시

```sql
CREATE DATA GRANT admin.sales_apac
  AS SELECT (ALL COLUMNS EXCEPT ssn, email)   -- 컬럼 마스킹
  ON   admin.customers                          -- 어느 객체
  WHERE region = 'APAC'                         -- 행 필터
    AND owner_id = ORA_END_USER_CONTEXT.username  -- 세션 정체성
  TO   sales_role;                              -- 누구에게 (그룹)
```

### VPD 와 비교 한 표

| | VPD | DDS |
|---|---|---|
| 정책 표현 | PL/SQL 함수 (절차형) | SQL DDL (선언형) |
| 컬럼 마스킹 | 별도 `DBMS_REDACT` | 그랜트에 내장 |
| 미권한 동작 | 0 rows | 객체 숨김 (`ORA-00942`) |
| 신원 | DB 유저 + LOGON 트리거 | END USER ± OAuth2 매핑 |
| 운영 코드 | 정책 함수 + 컨텍스트 패키지 + 트리거 | 그랜트 DDL 만 |
| 감사 | 함수 본문 까야 함 | 카탈로그 뷰 |

---

## 2. 시나리오 — HR `employees` 테이블 9-STEP

3 종 사용자:
- `hr_lead` — 전체 행, 모든 컬럼
- `mgr_eng` — Engineering 부서만, SSN 마스킹
- `auditor_ext` — 전체 행, salary/ssn/email 마스킹

### 두 가지 핵심 질문 직답

| Q | A |
|---|---|
| 유저는 어느 객체에 추가? | **어디에도 안 추가.** `CREATE END USER` 로 카탈로그(`dba_end_users`)에 생기는 독립 객체 |
| 일반 CREATE TABLE 에 같이 쓰나? | **아니오.** 테이블 DDL 은 평범하게. 정책은 별도 `CREATE DATA GRANT` |
| 테이블 정의를 바꿔야 하나? | **거의 안 바꿈.** 예외는 MAC 모드 켤 때 `ALTER TABLE ... SET USE DATA GRANTS ONLY ENABLED` 한 줄 |

### 9 스텝 요약

```text
STEP 0  ADMIN 으로 접속
STEP 1  CREATE TABLE employees (...)         -- 평범한 DDL
STEP 2  CREATE END USER "hr_lead" / "mgr_eng" / "auditor_ext"
STEP 3  CREATE ROLE dds_db_role + GRANT CREATE SESSION
        CREATE DATA ROLE hr_lead_role / eng_mgr_role / auditor_role
        GRANT dds_db_role TO <each data role>
STEP 4  GRANT DATA ROLE <role> TO "<user>"
STEP 5  CREATE DATA GRANT (행+컬럼+대상) × 3
STEP 6  각 유저로 접속해 결과 확인
STEP 7  CREATE OR REPLACE DATA GRANT 로 정책 라이브 변경
        DROP DATA GRANT 로 회수 (= 즉시 ORA-00942)
STEP 8  (선택) ALTER TABLE ... SET USE DATA GRANTS ONLY ENABLED  -- MAC 모드
STEP 9  감사 (dba_data_grants 등) + 역순 정리
```

전체 스크립트는 세션 본문 참조. 이 POC 의 `sql/adb/13_dds_variant.sql` + `15_dds_cleanup.sql` 가 동일 패턴.

---

## 3. 그룹 적용

### END USER 는 그룹이 아니다 — 그룹은 DATA ROLE

마케팅팀 N 명에게 같은 권한:

```sql
-- 1) 그룹 1 개
CREATE DATA ROLE marketing_role;
GRANT dds_db_role TO marketing_role;

-- 2) 정책 1 줄 — 그룹에 부여
CREATE DATA GRANT admin.dg_marketing_view
  AS SELECT (ALL COLUMNS EXCEPT ssn, salary)
  ON   admin.employees
  WHERE department = 'Marketing'
  TO   marketing_role;

-- 3) 멤버 N 명 추가 (반복)
GRANT DATA ROLE marketing_role TO "carol";
GRANT DATA ROLE marketing_role TO "david";

-- 멤버 제거
REVOKE DATA ROLE marketing_role FROM "frank";
```

정책은 한 번, 멤버 변경은 GRANT/REVOKE.

### DATA ROLE 중첩 (그룹 안에 그룹)

```sql
GRANT all_employees_role TO marketing_role;        -- 마케팅이 전사 권한 흡수
GRANT marketing_role     TO marketing_lead_role;   -- 리드가 마케팅 권한 흡수
```

### Federated Identity (외부 IdP 그룹 그대로)

```sql
CREATE DATA ROLE marketing_role MAPPED TO 'AZURE_ROLE=Marketing';
CREATE DATA ROLE finance_role   MAPPED TO 'OKTA_GROUP=Finance-Read';
```

→ DB 에 END USER 안 만들고, Azure/Okta 그룹 멤버십이 진실. SaaS / agentic AI 에 가장 큰 차별점.

---

## 4. DB Link (heterogeneous) 데이터 통제

### 패턴

```text
[원격 PG/MySQL]
   │ DB Link
   ▼
[ADB: CREATE VIEW v_dds_customers_pg AS SELECT ... FROM ...@RDS_POSTGRES_LINK]
   │ CREATE DATA GRANT ON v_dds_customers_pg
   ▼
[DATA ROLE] ──GRANT──▶ [END USER]
```

### 핵심 4 가지 주의점

| 주의 | 내용 |
|---|---|
| 1. 원격 객체에 직접 ❌ | DATA GRANT 는 로컬 객체에만. 무조건 VIEW 한 겹 |
| 2. VPD 와 같은 뷰 공유 ❌ | VPD `1=0` 가 DDS 보다 먼저 적용 → silent 0 rows. **DDS 전용 뷰** 따로 만들 것 |
| 3. WHERE 푸시다운 보장 X | 옵티마이저 판단. 큰 테이블이면 `EXPLAIN PLAN` 으로 REMOTE 노드 확인 |
| 4. MV 는 다른 얘기 | MV 만들면 데이터 캐시됨 → fresh 정책 트레이드오프 |

이 POC 의 `v_customers_*` (VPD용) ↔ `v_dds_customers_*` (DDS용) 분리가 정확히 #2 회피 때문.

---

## 5. OCI Data Catalog 객체 통제

### 모델 — 2 층 권한

| 층 | 무엇 | 누가 관리 |
|---|---|---|
| ① OCI IAM | Object Storage 버킷 + Data Catalog 자산 자체 | OCI 콘솔 (IAM Policy) |
| ② DDS | ADB 안에 동기화된 External Table/View | ADB ADMIN |

DDS 는 ②만. ①에서 ADB credential 이 가진 권한이 천장.

### 흐름

```text
[Object Storage / RDB]
   │ (등록)
   ▼
[OCI Data Catalog]
   │ DBMS_DCAT.RUN_SYNC
   ▼
[ADB: External Table DCAT$XYZ]   ← 평범한 ADB 객체
   │
   ▼
[ADB: VIEW v_sales]   ◀── CREATE DATA GRANT ON v_sales TO ...
```

### 스크립트 골격

```sql
-- 1) Catalog 연결 (최초 1회)
BEGIN
  DBMS_DCAT.SET_DATA_CATALOG_CONN(
    region => 'ap-seoul-1',
    catalog_id => 'ocid1.datacatalog.oc1...',
    catalog_tenancy => 'ocid1.tenancy.oc1...');
END;
/

-- 2) 동기화 — External Table 생성됨
BEGIN
  DBMS_DCAT.RUN_SYNC(
    synced_objects => '{"asset_list":[...]}',
    sync_mode      => 'AUTO',
    target_schema  => 'DCAT_LANDING',
    sync_response  => :resp);
END;
/

-- 3) sync-안전 VIEW 한 겹 (★)
CREATE OR REPLACE VIEW admin.v_customers AS
SELECT customer_id, full_name, email, region, signup_date
FROM   dcat_landing."DCAT$CUSTOMERS";

-- 4) DDS 정책
CREATE DATA GRANT admin.dg_sales_apac_customers
  AS SELECT (ALL COLUMNS EXCEPT email)
  ON   admin.v_customers
  WHERE region = 'APAC'
  TO   sales_apac_role;

-- 5) (선택) MAC
ALTER TABLE admin.v_customers SET USE DATA GRANTS ONLY ENABLED;
```

### 왜 VIEW 한 겹이 중요한가

`DBMS_DCAT.RUN_SYNC` 가 자산 변경 시 External Table 을 DROP/CREATE 할 수 있다.
External Table 에 직접 DATA GRANT 붙이면 sync 한 번에 정책이 사라진다.
**VIEW 를 사이에 끼우면 DATA GRANT 는 VIEW 에 붙으니 sync 와 무관하게 살아남는다.**
(DB Link 패턴과 동일한 이유)

---

## 6. "row 형태로 권한 적용" 이라는 표현

### 80% 맞지만 부족

| 통제 | 표현 |
|---|---|
| 행 | `WHERE region = 'APAC'` |
| 컬럼 | `(ALL COLUMNS EXCEPT email, ssn)` |
| 작업 | `AS SELECT/INSERT/UPDATE/DELETE` |
| 객체 노출 | 그랜트 없으면 `ORA-00942` |

정확한 한 줄: **"행 + 컬럼 + 작업 단위 권한을, 그룹(DATA ROLE) 대상으로 선언형 DDL 한 줄에 표현."**

### 흔한 오해 — 권한 저장 매체가 다름

| | VPD | DDS |
|---|---|---|
| 권한 어디 저장? | `permission` **테이블의 행** | DB 카탈로그의 `DATA GRANT` **객체** |
| 권한 추가 | `INSERT INTO permission ...` | `CREATE DATA GRANT ...` |
| 권한 회수 | `DELETE FROM permission ...` | `DROP DATA GRANT ...` |
| 권한 변경 | `UPDATE permission SET ...` | `CREATE OR REPLACE DATA GRANT ...` |

DDS = "권한을 데이터처럼 다루는" 게 아니라 "**권한을 SQL 객체로 다룬다**" (GRANT 처럼).

---

## 7. 권한 매핑 테이블 모델 — VPD vs DDS

### 결론: **매핑 테이블 패턴은 VPD 가 맞다.**

VPD 정책 함수는 PL/SQL → 그 안에서 임의의 SELECT 가능 → 매핑 테이블 lookup 자연스러움.
DDS 의 DATA GRANT 는 선언형 DDL → 동적 lookup 불가능.

### 운영 패턴 비교

| 운영 | VPD | DDS |
|---|---|---|
| 권한 추가 | `INSERT INTO permission ...` | `CREATE DATA GRANT ...` 또는 `GRANT DATA ROLE` |
| 누가 변경? | 매핑 테이블에 INSERT 권한자 (앱/사람, DBA 아님) | DDL 권한자 (ADMIN) |
| 셀프서비스 (앱이 권한 위임) | 자연스러움 | 부자연 (앱이 DDL 실행은 부담) |
| 수만 명 운영 | 매핑 행 N → 함수가 동적 처리 (확장 잘 됨) | DATA GRANT/DATA ROLE 폭증 시 부담 |

### DDS 우회 (권장 X)

DATA GRANT 대상 객체를 **매핑 테이블과 JOIN 한 뷰** 로 만들 수 있다.

```sql
CREATE OR REPLACE VIEW admin.v_customers_scoped AS
SELECT c.*
FROM   admin.customers c
JOIN   admin.permission p ...
JOIN   admin.user_group ug ...
WHERE  ug.user_name = ORA_END_USER_CONTEXT.username
  AND  INSTR(',' || p.allowed_regions || ',', ',' || c.region || ',') > 0;

CREATE DATA GRANT admin.dg_scoped
  AS SELECT ON admin.v_customers_scoped TO some_role;
```

→ 동작은 한다. 그러나:
- 선언형 DDS 의 장점 상실 (로직이 뷰 SQL 로 옮겨갔을 뿐)
- 매핑 테이블 별도 보호 필요
- 디버깅이 두 곳 (뷰 + 그랜트) 으로 나뉨
- **이럴 거면 그냥 VPD 가 깔끔**

### 선택 가이드

| 상황 | 권장 |
|---|---|
| 권한이 **데이터 자체** (테넌트·고객·부서 매핑 자주 변경, 앱이 관리) | **VPD** |
| 권한이 **운영 정책** (소수 역할, DBA/SRE 가 DDL 관리, IdP 연동) | **DDS** |
| **수만 행 × 동적 lookup** (멀티테넌트 SaaS) | **VPD** |
| **선언형 단일 평면 + 카탈로그 감사** 우선 | **DDS** |
| **OAuth2 / Azure AD 그룹** 그대로 사용 | **DDS** (`MAPPED TO`) |

### 이 POC 에서 보면

- `sql/adb/06_policy.sql` + `permission` 테이블 = "앱/운영자가 행으로 권한 관리" → **VPD 답안**
- `sql/adb/13_dds_variant.sql` = "DBA 가 DDL 로 4 종 역할 정의" → **DDS 답안**
- 같은 결과를 두 다른 모델로 표현한 비교 데모

**권한 매핑 테이블 중심으로 가겠다면 VPD 그대로 두는 게 정답.**
DDS 로 옮기려면 매핑 자체를 `DATA ROLE 멤버십` 으로 재구성해야 한다 (매핑 테이블 사라지고 `GRANT DATA ROLE` 이 그 자리 차지).

---

## 부록 — DDS 운영 시 자주 보는 쿼리

```sql
-- 누가 뭘 볼 수 있는지
SELECT * FROM dba_data_grants;

-- 데이터 역할 (그룹)
SELECT * FROM dba_data_roles;

-- 데이터 사용자
SELECT * FROM dba_end_users;

-- 일반 ROLE ↔ DATA ROLE 매핑 관계
SELECT grantee, granted_role
FROM   dba_role_privs
WHERE  grantee LIKE '%role';

-- MAC 모드 켜진 객체
SELECT owner, table_name
FROM   dba_tables
WHERE  use_data_grants_only = 'YES';   -- 정확한 컬럼명은 카탈로그 확인
```

---

## 다음 세션 재개 시 확인할 것

- 이 POC 의 DDS 변형은 이미 E2E 검증 완료 (Task #28). `./run.sh dds` 한 번이면 재현됨.
- README 의 `## 무엇을 통제하는가` + `## DDS 설정 핵심` 섹션은 이번에 추가됨.
- 매핑 테이블 패턴 = VPD, 선언형 패턴 = DDS — POC 가 두 길 다 보여줌.
- 확장 검토 후보:
  - DDS 변형에 `MAPPED TO` (federated identity) 데모 추가
  - DB Link 외에 OCI Data Catalog 싱크 자산을 같은 DDS 매트릭스로 보호하는 변형
  - `SET USE DATA GRANTS ONLY` 활성화 후 우회 시도 5종 재검증
