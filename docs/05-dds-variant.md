# 05 · DDS Variant — 같은 데모를 Oracle Deep Data Security 로

> 본 문서는 **선택사항** 입니다. 메인 데모(`run.sh all`) 는 전통적 **VPD** 경로로
> 그대로 동작합니다. 이 문서는 동일한 4-user 매트릭스를 Oracle AI Database
> **26ai 신기능 Deep Data Security (DDS)** 로 재구현한 `sql/adb/13_dds_variant.sql`
> 의 사용법과, VPD ↔ DDS 1:1 매핑을 다룹니다.

---

## 1. 왜 DDS 도 같이 다루는가

Oracle 은 **2026-04-09** 에 VPD/RAS 의 공식 후계자로 **Deep Data Security**
(이하 DDS) 를 발표했습니다 (Oracle AI Database 26ai). 공식 한 줄:

> "Extends and modernizes Oracle Virtual Private Database and Real Application
> Security, moving from procedural PL/SQL and API-based controls **to
> declarative policies in SQL**."

본 POC 는 의도적으로 **VPD 를 직접 구현하는 경로** 를 보여주는데, 같은 요건을
DDS 로 표현하면 코드량과 메커니즘이 어떻게 줄어드는지 함께 보여주면 의사결정
근거가 명확해집니다.

---

## 2. 전제 조건

| 항목 | 요건 |
|---|---|
| Oracle 버전 | AI Database **23.26.2 이상** (`v$version` 확인) |
| 초기화 파라미터 | `COMPATIBLE >= 20.0` (`SELECT value FROM v$parameter WHERE name='compatible'`) |
| 관리자 권한 | `CREATE END USER`, `CREATE DATA ROLE`, `GRANT DATA ROLE`, `CREATE DATA GRANT` (ADB ADMIN 은 기본 보유) |

> ADB 가 아직 26ai 로 업그레이드되지 않은 환경이라면 이 스크립트는 `ORA-00942`
> 류로 실패합니다. 본 디렉토리의 VPD 경로는 그대로 동작합니다.

---

## 3. 실행

```bash
source .env                              # DDSUSER_*_PASSWORD 로드
sqlplus "$ADB_USER/$ADB_PASSWORD@$ADB_TNS" @sql/adb/13_dds_variant.sql
```

스크립트가 만드는 객체:

```
END USER  ddsuser_my   ddsuser_pg   ddsuser_both   ddsuser_none
ROLE      dds_db_role           (CREATE SESSION 보유)
DATA ROLE my_only_role   pg_only_role   both_sources_role
DATA GRANT
  dds_my_only_grant_mysql   -> v_customers_my  TO my_only_role
  dds_pg_only_grant_pg      -> v_customers_pg  TO pg_only_role
  dds_both_grant_pg         -> v_customers_pg  TO both_sources_role
  dds_both_grant_mysql      -> v_customers_my  TO both_sources_role
```

검증:

```bash
sqlplus '"ddsuser_pg"'/"$DDSUSER_PG_PASSWORD"@"$ADB_TNS" <<'EOF'
SELECT ORA_END_USER_CONTEXT.username FROM dual;
SELECT COUNT(*) FROM admin.v_customers_pg;   -- 12 (기대값)
SELECT COUNT(*) FROM admin.v_customers_my;   -- ORA-... 권한 없음 (기대)
EOF
```

---

## 4. VPD ↔ DDS 1:1 매핑

| 관심사 | VPD 경로 (이 POC 본체) | DDS 경로 (`13_dds_variant.sql`) |
|---|---|---|
| 사용자 생성 | `CREATE USER vpduser_my IDENTIFIED BY ...` | `CREATE END USER "ddsuser_my" IDENTIFIED BY ...` |
| 사용자 = 스키마? | 예 (Oracle 의 전통적 모델) | **아니오** — END USER 는 스키마/객체 없음 |
| 그룹/역할 매핑 | `app_group` + `user_group` 매핑 테이블 | `CREATE DATA ROLE` + `GRANT DATA ROLE x TO "user"` |
| 정책 표현 | `dbms_rls.add_policy` + PL/SQL 함수 `vpd_region_filter` | `CREATE DATA GRANT ... AS SELECT ON tbl WHERE ... TO role` (선언형 SQL) |
| 식별자 전파 | LOGON 트리거 → `DBMS_SESSION.SET_CONTEXT('VPD_CTX', ...)` | 세션 자동: `ORA_END_USER_CONTEXT.username` |
| 행 필터 | 정책 함수가 `WHERE region IN (...)` 반환 | `CREATE DATA GRANT ... WHERE region IN ('APAC','EMEA')` |
| 컬럼 마스킹 | 별도 `DBMS_REDACT.ADD_POLICY` (06a_redaction.sql) | 같은 grant 안에 `AS SELECT (ALL COLUMNS EXCEPT email)` |
| 권한 부여 단위 | `permission` 테이블 INSERT | DDL 한 줄 (`CREATE DATA GRANT`) |
| 우회 차단 | GRANT 안 주기 + EXEMPT ACCESS POLICY 안 주기 + DBMS_RLS 안 주기 | `SET USE DATA GRANTS ONLY ON tbl ENABLED` (MAC) 한 줄 |

---

## 5. 행/컬럼 변형 — 한 줄 비교

**행 필터 (region='APAC' 만):**

```sql
-- VPD: 정책 함수 안에서 IN-list 빌드 + permission 테이블 UPDATE
UPDATE permission SET allowed_regions = 'APAC' WHERE group_id = 30 ...;

-- DDS: grant 한 줄
CREATE OR REPLACE DATA GRANT admin.dds_both_grant_pg
  AS SELECT
  ON admin.v_customers_pg
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
  ON admin.v_customers_pg
  TO both_sources_role;
```

---

## 6. 한계와 주의

* DDS Data Grant 는 **테이블·뷰** 모두에 적용 가능. 본 POC 의 `v_customers_*`
  뷰는 DB Link 패스스루이므로 grant 가 뷰 단에서 평가됩니다. 원격 push-down 은
  여전히 ADB → 원격 옵티마이저 협상에 의존합니다 (VPD 와 동일한 성능 특성).
* 본 스크립트는 의도적으로 `USE DATA GRANTS ONLY` (MAC) 를 **켜지 않습니다**.
  켜면 `vpduser_*` (regular GRANT 경로) 가 같은 뷰에 접근하지 못해 VPD 데모가
  깨집니다. 운영에서는 MAC 활성화가 권장됩니다.
* OAuth2 identity propagation (OCI IAM / Microsoft Entra ID claim) 은 본
  문서에선 다루지 않습니다. 직접 로그온 시나리오만 다룹니다. agentic AI / ORDS
  중간 계층에서 사용자 ID 를 전파하는 경우 4장 (OCI IAM) / 5장 (Entra ID) 의
  공식 가이드 (`G50191`) 를 참고하세요.

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
DROP ROLE dds_db_role;
DROP END USER "ddsuser_my";
DROP END USER "ddsuser_pg";
DROP END USER "ddsuser_both";
DROP END USER "ddsuser_none";
```
