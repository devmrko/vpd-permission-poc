# 02 · Architecture

## 한 줄 요약

> **원본 DB 는 그냥 데이터 창고**, **권한은 ADB 한 곳**, **VPD 가 SQL 에 WHERE 절을
> 자동 주입**.

---

## 왜 이렇게 만들었나 — 쿼리마다 EXISTS 돌리지 않는다

가장 단순한 접근은 "VPD 정책 함수 안에서 매번 매핑 테이블에 EXISTS 쿼리를 던지는"
방식입니다. 동작은 하지만 **모든 SELECT 마다 권한 lookup 이 한 번 더 일어나서**
원격 DB Link 위의 조회를 두 배로 만듭니다.

이 PoC 는 그 대신 **"로그인 시 1회만 매핑 조회 → 결과를 세션 컨텍스트에 캐시"**
패턴을 씁니다.

```
[LOGON 1회]                                  [SELECT N회]
ctx_pkg.init                                 vpd_region_filter
   │                                              │
   ├─ app_user JOIN user_group                    ├─ SYS_CONTEXT 읽기 (메모리)
   │   JOIN permission JOIN db_source             │   = 디스크/네트워크 I/O 0
   │   → 'APAC' 또는 'APAC,EMEA' 또는 '*' 또는 NULL
   │                                              ├─ NULL → '1=0' (fail closed)
   └─ SYS_CONTEXT('VPD_CTX', '<view>') 에 저장    ├─ '*'  → NULL (필터 없음)
                                                  └─ 그 외 → 'region IN (...)'
                                                            ← Oracle 이 WHERE 에 AND
```

**즉 EXISTS subquery 가 아니라 IN-list predicate 입니다.** 정책 함수가 반환한
predicate 문자열을 Oracle 이 SQL 에 자동으로 AND 붙여줍니다. 매핑 테이블은
LOGON 시 한 번만 읽으므로 쿼리 부하 0.

캐시가 stale 해질 위험 — 권한 변경 후 즉시 반영 — 은 `ctx_pkg.init` 을 수동
호출하거나 재로그인으로 해소합니다. 운영에선 권한 변경 빈도가 낮으니 합리적
트레이드오프입니다.

---

## 데이터 흐름

```
            +---------------------+
            |  vpduser_my / pg /  |  ← 사용자 로그인
            |  both / none        |
            +----------+----------+
                       |
              (1) LOGON 트리거
                       |
                       v
            +---------------------+
            | ctx_pkg.init        |  → vpd_ctx 컨텍스트에 권한 set
            +----------+----------+
                       |
            (2) SELECT * FROM v_customers_pg
                       |
                       v
            +---------------------+
            | VPD 정책 함수       |  → SYS_CONTEXT 읽고 WHERE 절 동적 생성
            | (region IN (...))   |
            +----------+----------+
                       |
            (3) 뷰가 DB Link 로 원격 조회
                       |
            +----------+----------+
            |  RDS_POSTGRES_LINK  |  (DBMS_CLOUD_ADMIN heterogeneous)
            |       OR            |
            |  RDS_LINK (MySQL)   |
            +----------+----------+
                       |
                       v
            +---------------------+
            | 원격 customers      |  → 필터링된 행만 ADB 로 반환
            +---------------------+
                       |
            (4) Data Redaction 으로 이메일 마스킹
                       |
                       v
                   사용자 화면
```

---

## 권한 모델 (6 테이블)

| 테이블 | 의미 |
|---|---|
| `app_customer` | 도메인의 "고객사" 개념 (멀티 테넌트 hook) |
| `app_user` | DB 유저 → 도메인 유저 매핑 (`db_username` 컬럼이 핵심) |
| `app_group` | 그룹 (MY_ONLY, PG_ONLY, BOTH_SOURCES, ...) |
| `user_group` | user ↔ group N:N |
| `db_source` | 원본 소스 식별자 (PG, MY) |
| `permission` | (group, source, region) 행 — `region='*'` 이면 전체 허용 |

→ "이 유저는 어떤 region 을 볼 수 있는가?" 는 **`app_user → user_group → permission` JOIN**
한 번이면 답이 나옵니다. 그게 정책 함수가 하는 일.

---

## 정책 함수 (`vpd_region_filter`)

`DBMS_RLS.ADD_POLICY` 가 뷰 호출 시마다 부르는 함수. 반환 문자열이 그대로
**WHERE 절 뒤에 AND 로 붙습니다.**

핵심 로직:

1. `SYS_CONTEXT('VPD_CTX', 'ALLOWED_REGIONS_PG')` 를 읽음
   (값 예: `'APAC'` 또는 `'APAC,EMEA,AMER'` 또는 `NULL`)
2. NULL/빈 값 → `RETURN '1=0'` (아무것도 안 보임 = fail closed)
3. `'*'` 포함 → `RETURN NULL` (필터 없음 = 전체)
4. 그 외 → `RETURN 'region IN (''APAC'',''EMEA'')'` 형식으로 in-list

→ 컨텍스트는 **Secure Application Context** (`USING ctx_pkg`) 라 다른 패키지에서
설정 불가. 엔드유저가 `DBMS_SESSION.SET_CONTEXT('VPD_CTX', ...)` 직접 호출 →
ORA-01031.

---

## 왜 뷰를 한 단계 더 두나?

뷰가 없으면 정책을 **DB Link 위에 직접** 걸어야 하는데, VPD 는 원격 객체를
직접 보호할 수 없습니다. 그래서:

* `v_customers_pg` 는 `SELECT ... FROM "public"."customers"@RDS_POSTGRES_LINK`
  하는 단순 통과 뷰
* VPD 정책은 이 **로컬 뷰** 에 붙음
* 엔드유저에게는 뷰만 GRANT, DB Link 는 **EXECUTE 권한 X / 직접 보지 못함**

---

## 신뢰 경계

| 누가 | 무엇을 할 수 있나 |
|---|---|
| `ADMIN` (ADB 관리자) | 전부 다. 정책 BYPASS. **운영에선 절대 일반 사용자에게 주지 말 것** |
| `vpduser_*` (my/pg/both/none) | (a) 자기에게 GRANT 된 뷰 SELECT, (b) `ctx_pkg.init` 호출 |
| `vpduser_*` 가 **못** 하는 것 | DBMS_RLS 변경, EXEMPT ACCESS POLICY, CREATE TABLE (스냅샷 방지), 매핑 테이블 직접 SELECT, DB Link 직접 SELECT |

→ 즉 엔드유저 입장에서 정책을 우회할 표면이 없습니다. `07_end_users.sql` 의
주석에서 "what we are deliberately NOT granting" 섹션이 그 목록.

---

## 한계 / 운영 고려사항

* **DB Link 비밀번호** 는 `DBMS_CLOUD.CREATE_CREDENTIAL` 로 ADB 안에 저장되지만,
  ADMIN 은 평문에 가깝게 읽을 방법이 있습니다 (deferred admin 모델). 운영에선
  IAM/Vault 기반 credential 회전이 필요.
* **LOGON 트리거 실패 = fail closed** (컨텍스트 비어있음 → `1=0`). 운영에선
  실패시 audit table 기록 추가 권장.
* **성능**: 정책 함수는 statement-level 캐싱이 기본. 같은 세션에서 같은 statement
  를 반복 실행해도 한 번만 호출됨. 하지만 통합 뷰가 DB Link 위에 있어 **원격
  쿼리는 매번** 발생.
* **Redaction 정책**은 row level filter 와 별개로 동작 — 마스킹 컬럼이 추가되면
  06a 에 추가 필요.
