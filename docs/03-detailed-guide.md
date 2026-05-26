# VPD 기반 권한 관리 POC — 상세 설명서

> **이 문서의 대상 독자:** 데이터베이스 보안이나 Oracle 기술에 익숙하지 않은 분.
> 용어가 나올 때마다 풀어서 설명하고, 왜 그 장치가 필요한지를 함께 적습니다.

> **시나리오 안내:** 현재 `./run.sh` 가 자동으로 깔아주는 기본 시나리오는 4명의
> 엔드유저 (`vpduser_my`/`pg`/`both`/`none`) 가 각각 다른 소스에 접근 가능한 **소스
> 단위 매트릭스** 입니다 (README 참고). 본 상세 가이드는 그 위에 얹을 수 있는 **행
> 단위 region 필터링** 변형(`KR_ANALYSTS → APAC`, `GLOBAL_ADMINS → '*'`) 을 예시로
> 사용합니다 — VPD 메커니즘 자체는 동일하므로 개념 이해에는 차이가 없습니다.
> region 필터를 실제로 켜려면 `sql/adb/03_seed.sql` 하단의 주석을 해제하세요.

---

## 목차

1. [무엇을 만들었고, 왜 만들었나](#1-무엇을-만들었고-왜-만들었나)
2. [핵심 개념 풀어보기](#2-핵심-개념-풀어보기)
3. [전체 구조도](#3-전체-구조도)
4. [구성 요소별 설명](#4-구성-요소별-설명)
5. [실제 동작 결과 (테스트 출력)](#5-실제-동작-결과-테스트-출력)
6. [“정말로 못 우회하나?” — 보안 보장 7 계층](#6-정말로-못-우회하나--보안-보장-7-계층)
7. [추가 강화 옵션 (운영 단계용)](#7-추가-강화-옵션-운영-단계용)
8. [현재 POC의 한계와 운영 시 고려사항](#8-현재-poc의-한계와-운영-시-고려사항)
9. [디렉터리·파일 구조](#9-디렉터리파일-구조)
10. [실행 방법](#10-실행-방법)
11. [데이터 카탈로그 인프라로서의 가능성 — Databricks Unity Catalog 와 비교](#11-데이터-카탈로그-인프라로서의-가능성--databricks-unity-catalog-와-비교)

---

## 1. 무엇을 만들었고, 왜 만들었나

### 1-1. 풀고 싶은 문제

회사에는 **여러 개의 데이터베이스**가 있습니다. 어떤 데이터는 Oracle에, 어떤 데이터는 MySQL에, 어떤 데이터는 PostgreSQL에 들어 있어요. 분석가, 운영자, 외부 협력사 사람들이 각자 필요한 데이터만 볼 수 있어야 하는데, 각 데이터베이스마다 권한을 따로 설정하면 다음과 같은 문제가 생깁니다.

- **관리가 흩어진다**: “이 사람한테 어디까지 보여주고 있더라?” 를 추적하기 어렵습니다.
- **일관성이 깨진다**: MySQL에는 권한을 줬는데 Postgres에는 못 줬더라, 같은 실수가 흔합니다.
- **변경이 느리다**: 한 사람의 권한을 바꾸려면 여러 시스템을 동시에 손봐야 합니다.

### 1-2. 해결 아이디어

이 POC의 핵심 아이디어는 **“하나의 관문(Oracle ADB)** 을 둬서 모든 사용자 질의(SELECT 문)를 거기로만 받게 하고, 그 관문 안에서 ‘이 사용자에게 어디까지 보여줄지’를 자동으로 결정하자”입니다.

- **관문**: Oracle Autonomous Database (이하 **ADB**). 우리가 정책을 적용할 단 한 곳.
- **외부 데이터들**: ADB가 **DB Link** 라는 통로로 MySQL/Postgres에 연결되어 있어, 마치 “ADB 안의 테이블”처럼 조회할 수 있습니다.
- **자동 결정**: 사용자가 `SELECT * FROM 고객테이블` 이라고만 쓰면, Oracle이 알아서 “이 사용자가 KR 분석가니까 region='APAC'인 행만 보여줘야지” 하고 `WHERE region='APAC'` 을 **자동으로 붙여서** 실행합니다. 이걸 **VPD (Virtual Private Database)** 라고 합니다.

### 1-3. 한 줄 요약

> 사용자가 단순한 SELECT 한 줄을 던져도, Oracle이 그 사람의 신분증을 보고 알아서 “보여줄 수 있는 행만” 골라줍니다. 사용자는 자기가 필터 받았다는 것조차 모를 수 있습니다.

---

## 2. 핵심 개념 풀어보기

### 2-1. VPD (Virtual Private Database)란?

“가상 사설 데이터베이스”라는 뜻인데, 이름이 좀 어렵게 들리지만 개념은 단순합니다.

**비유**: 회사 도서관에 책이 1만 권 있다고 합시다. 사람마다 열람 가능한 책이 다릅니다.
- **방식 A (전통)**: 사람마다 다른 도서관 건물을 만든다. (= 데이터를 복제) → 비싸고, 동기화 안 됨.
- **방식 B (VPD)**: 도서관은 하나. 그런데 들어오는 사람의 신분증을 보고 사서가 **자동으로** 그 사람이 못 보는 책장 앞에 가림막을 친다. 그 사람은 1만 권 중 자기에게 허용된 책만 보입니다.

VPD는 “방식 B”의 사서 역할을 Oracle이 자동으로 해 주는 기능입니다.

### 2-2. DB Link란?

ADB 안에 앉아서 `SELECT * FROM customers@RDS_LINK` 라고 쓰면, ADB가 알아서 AWS RDS의 MySQL까지 다녀와서 결과를 보여줍니다. 즉, **“다른 DB로 가는 지름길”** 같은 것입니다. 사용자 입장에선 외부 DB가 어디에 있든 ADB 안의 테이블처럼 보입니다.

이 POC의 ADB에는 두 개의 DB Link가 이미 만들어져 있습니다.

| DB Link 이름 | 가리키는 곳 |
|---|---|
| `RDS_LINK` | AWS RDS MySQL (`ecommerce_poc` 스키마) |
| `RDS_POSTGRES_LINK` | AWS RDS PostgreSQL (`public` 스키마) |

### 2-3. Application Context (애플리케이션 컨텍스트)란?

사용자가 ADB에 로그인하면 그 세션(연결)이 살아 있는 동안 “이 사람은 누구다”, “어디까지 볼 수 있다” 같은 정보를 **세션 메모리**에 저장해 둘 수 있습니다. 이걸 컨텍스트라고 부릅니다.

**비유**: 도서관에 들어올 때 받는 “오늘 출입증 + 열람권”. 출입증을 받은 사람은 도서관 안에서 굳이 매번 신분증을 꺼내지 않아도 됩니다. 사서(=Oracle)가 그 사람의 출입증을 자동으로 들여다봅니다.

POC에서는 이 컨텍스트를 **`VPD_CTX`** 라는 이름으로 만들어서, 로그인할 때 자동으로 “이 사용자가 볼 수 있는 region 목록”을 채워 둡니다.

### 2-4. Secure Application Context (보안 컨텍스트)

그냥 컨텍스트는 누구나 자기 마음대로 “나는 관리자야!”라고 적어 넣을 수 있어서 위험합니다. 그래서 Oracle은 **“이 컨텍스트는 OO이라는 패키지(코드 묶음) 안에서만 설정될 수 있다”** 고 못 박을 수 있습니다.

POC에서는:
```sql
CREATE OR REPLACE CONTEXT vpd_ctx USING ctx_pkg;
```
이 한 줄이 핵심입니다. `vpd_ctx` 컨텍스트는 오직 `ctx_pkg` 라는 패키지 안의 코드만이 값을 채울 수 있습니다. 사용자가 직접 `DBMS_SESSION.SET_CONTEXT('VPD_CTX','...')` 를 호출하면 **`ORA-01031: insufficient privileges`** 로 거절당합니다.

### 2-5. LOGON 트리거란?

“사용자가 데이터베이스에 로그인하는 순간, 자동으로 실행되는 작은 프로그램”입니다. 사용자가 뭘 하기 전에 시스템이 먼저 “환영합니다, 당신의 권한을 세팅해 둘게요” 라고 준비하는 역할이죠.

POC에서는 VPDUSER_A 또는 VPDUSER_B가 ADB에 로그인하는 순간 트리거가 발동되어 `ctx_pkg.init` 을 부르고, 그 결과로 `vpd_ctx` 컨텍스트에 그 사용자의 권한이 채워집니다. **사용자가 막을 수 없습니다.** 트리거를 끄거나 우회할 권한 자체를 주지 않았기 때문입니다.

---

## 3. 전체 구조도

```
┌──────────────────────────────────────────────────────────────────────────┐
│                          ADB (Oracle Autonomous DB)                       │
│                                                                            │
│   ┌──────────────┐                                                         │
│   │ VPDUSER_A    │   ① 로그인                                              │
│   │ VPDUSER_B    │ ─────────► ┌─────────────────────────┐                  │
│   └──────────────┘            │   LOGON 트리거           │                  │
│         │                     │   = vpd_logon_trg        │                  │
│         │                     └──────────┬──────────────┘                  │
│         │                                │ ② ctx_pkg.init 호출             │
│         │                                ▼                                  │
│         │                     ┌─────────────────────────┐                  │
│         │                     │  ctx_pkg.init           │                  │
│         │                     │   - permission 테이블 조회 │                  │
│         │                     │   - SET_CONTEXT          │                  │
│         │                     └──────────┬──────────────┘                  │
│         │                                │ ③ 세션 메모리에 권한 저장        │
│         │                                ▼                                  │
│         │                     ┌─────────────────────────┐                  │
│         │                     │  VPD_CTX (보안 컨텍스트) │                  │
│         │                     │   USER_ID=1              │                  │
│         │                     │   V_CUSTOMERS_PG='APAC'  │                  │
│         │                     │   V_CUSTOMERS_MY='APAC'  │                  │
│         │                     └─────────────────────────┘                  │
│         │                                                                   │
│         │  ④ SELECT * FROM v_customers_pg                                  │
│         ▼                                                                   │
│   ┌─────────────────────────────┐                                          │
│   │  v_customers_pg (로컬 뷰)    │ ← VPD 정책 부착                          │
│   └──────┬──────────────────────┘   ⑤ vpd_region_filter 함수 호출         │
│          │                          │                                       │
│          │                          ▼ "region IN ('APAC')" 반환             │
│          │                                                                   │
│          │  ⑥ 사용자의 질의에 WHERE 절 자동 결합:                          │
│          │     SELECT * FROM "public"."customers"@RDS_POSTGRES_LINK         │
│          │     WHERE region IN ('APAC')                                     │
│          ▼                                                                   │
│   ┌────────────────────────────────────────────────┐                       │
│   │  DB Link: RDS_POSTGRES_LINK / RDS_LINK         │                       │
│   └────────────────┬───────────────────────────────┘                       │
└────────────────────┼───────────────────────────────────────────────────────┘
                     │
                     ▼
         ┌───────────────────────┐    ┌───────────────────────┐
         │  AWS RDS PostgreSQL    │    │  AWS RDS MySQL         │
         │  public.customers      │    │  ecommerce_poc.customers│
         └───────────────────────┘    └───────────────────────┘
```

---

## 4. 구성 요소별 설명

POC는 9개의 SQL 파일로 이루어져 있고, 각각 한 가지 역할을 합니다.

### 4-1. `sql/00_cleanup.sql` — 초기화

POC를 몇 번이고 새로 깔 수 있도록, 이전에 만든 것을 모두 지웁니다. 처음 실행할 땐 “없는데 지우라고?” 하는 에러가 나도 무시하도록 `EXCEPTION WHEN OTHERS THEN NULL` 처리를 합니다.

### 4-2. `sql/01_perm_tables.sql` — 권한 모델 (정책 평면)

“누가, 어느 그룹에 속하고, 그 그룹은 어느 데이터 소스의 어느 객체에서 어떤 행을 볼 수 있는가”를 저장하는 테이블 6개를 만듭니다.

```
app_customer   ──┐
                 ├── 회사 단위 격리 (멀티테넌트 대비)
app_user      ──┤
                 │   ↑ 각 사용자는 1개 회사에 속함
app_group     ──┤
                 │   ↑ 그룹도 회사 단위로 관리
user_group    ──┘   ↑ 사용자 ↔ 그룹 N:M

db_source        ── 외부 데이터 소스 정의 (어느 DB Link인지)

permission       ── 핵심: 그룹 × 소스 × 객체 → 허용 region 목록
```

`permission.allowed_regions` 컬럼은 `'APAC'` 처럼 콤마로 구분된 값이거나 `'*'` 입니다. `'*'` 는 “모든 region 허용 = 필터 없음”을 뜻합니다.

### 4-3. `sql/02_seed.sql` — 시연용 데이터 주입

```
VPDUSER_A → KR_ANALYSTS 그룹     → APAC 만 조회 가능
VPDUSER_B → GLOBAL_ADMINS 그룹  → 전 region 조회 가능 (*)
```

이 두 사용자를 가지고 “같은 SQL 한 줄을 던졌을 때 결과가 다르다”는 것을 보일 겁니다.

### 4-4. `sql/03_secure_ctx.sql` — 보안 컨텍스트와 패키지

세 가지가 한 번에 만들어집니다.

1. **`ctx_pkg` 패키지의 명세**: `init` 이라는 프로시저가 있다고 선언.
2. **`ctx_pkg` 패키지의 본문**: 실제 로직.
   - 지금 로그인한 Oracle 사용자 이름을 알아낸다 (`SYS_CONTEXT('USERENV','SESSION_USER')`).
   - 그 사람을 `app_user` 테이블에서 찾는다.
   - `user_group` → `permission` 을 따라가서 “이 사람이 어느 객체에 어떤 region 까지 허용됐는지” 조회.
   - 결과를 `VPD_CTX` 컨텍스트 안에 객체별 속성으로 저장. (예: `VPD_CTX.V_CUSTOMERS_PG = 'APAC'`)
3. **`vpd_ctx` 보안 컨텍스트 자체**: `USING ctx_pkg` 라고 못 박아서, 다른 곳에서는 절대 이 값을 쓸 수 없게 막는다.

### 4-5. `sql/04_views.sql` — 외부 테이블에 대한 로컬 뷰

ADB 안에서 사용자가 보게 될 “테이블처럼 생긴 것”을 두 개 만듭니다. 둘 다 그저 외부 DB의 `customers` 테이블을 들여다보는 창문(view)일 뿐입니다.

- `v_customers_pg` → PostgreSQL의 `public.customers`
- `v_customers_my` → MySQL의 `ecommerce_poc.customers`

⚠️ **중요**: 사용자에게는 이 뷰만 보여줍니다. 뒤에 있는 `customers@RDS_POSTGRES_LINK` 같은 원본 참조는 **절대 직접 접근하게 두면 안 됩니다.** 그래야 정책을 우회할 수 없습니다.

### 4-6. `sql/05_policy.sql` — VPD 정책 함수와 부착

이게 마법의 핵심입니다.

1. **정책 함수 `vpd_region_filter`**: 사용자의 컨텍스트에서 허용 region 을 읽고, `region IN ('APAC')` 같은 문자열을 돌려준다.
   - 만약 컨텍스트에 아무것도 없으면 → **`1=0` 을 돌려준다.** (= “행 0개” = **fail-closed 안전장치**)
   - 만약 `'*'` 가 있으면 → `NULL` 을 돌려준다. (= 필터 없음 = 전부 보임)
2. **`DBMS_RLS.ADD_POLICY`** 로 위 함수를 `v_customers_pg`, `v_customers_my` 두 뷰에 “SELECT 문에 자동 결합되게” 부착.

이후 사용자가 `SELECT * FROM v_customers_pg` 라고 쓰면 Oracle이 내부적으로 `SELECT * FROM v_customers_pg WHERE region IN ('APAC')` 로 바꿔서 실행합니다. **사용자는 자기 쿼리에 WHERE이 추가됐다는 사실조차 모릅니다.**

### 4-7. `sql/06_end_users.sql` — 일반 사용자 계정과 LOGON 트리거

`VPDUSER_A`, `VPDUSER_B` 라는 일반 사용자 두 명을 만듭니다. 이 두 계정에 주는 권한은 **딱 4 가지**입니다.

```
✅ CREATE SESSION         (로그인할 권리)
✅ SELECT on v_customers_pg
✅ SELECT on v_customers_my
✅ EXECUTE on ctx_pkg     (트리거가 부를 수 있도록)
```

그 외에는 **아무것도 안 줍니다.** 특히:
```
❌ permission, app_user 등 권한 테이블 — 못 봄
❌ 원본 customers@DB_LINK — 못 봄
❌ DBMS_RLS — 정책을 끄거나 바꿀 권한 없음
❌ EXEMPT ACCESS POLICY — VPD를 무시할 시스템 권한 없음
❌ CREATE TABLE / CREATE MATERIALIZED VIEW — 필터된 결과를 스냅샷으로 빼낼 수 없음
❌ DBA 계열 ROLE 일체 없음
```

마지막으로 **LOGON 트리거** 를 만듭니다.

```sql
CREATE OR REPLACE TRIGGER vpd_logon_trg
AFTER LOGON ON DATABASE
BEGIN
  IF SYS_CONTEXT('USERENV','SESSION_USER') IN ('VPDUSER_A','VPDUSER_B') THEN
    admin.ctx_pkg.init;
  END IF;
END;
```

이 트리거는 데이터베이스 전체 단위로 “누가 로그인하든” 발동되고, 그 사람이 우리가 관리하는 사용자면 컨텍스트를 자동으로 채웁니다.

### 4-8. `sql/07_tests_user_a.sql`, `08_tests_user_b.sql`

각 사용자가 직접 쿼리해서, 정말 자기에게 허용된 행만 보이는지 확인. 그리고 5가지 **우회 시도** 가 전부 실패하는지 확인.

### 4-9. `sql/09_tests_admin_audit.sql`

관리자(ADMIN) 입장에서 “지금 어떤 정책들이 어느 뷰에 부착돼 있나?”, “누구한테 어떤 권한이 주어졌나?” 를 한눈에 보기 위한 감사용 스크립트. 다음 네 가지를 출력합니다.

1. **Attached VPD policies** — `DBA_POLICIES` 에서 `V_CUSTOMERS_PG`, `V_CUSTOMERS_MY` 에 붙은 정책(`CUSTOMERS_PG_POLICY`, `CUSTOMERS_MY_POLICY`) 과 활성화 상태.
2. **Attached Data Redaction policies** — `REDACTION_POLICIES` 에서 `PII_REDACT_PG`, `PII_REDACT_MY` 의 게이팅 식(`SYS_CONTEXT('VPD_CTX','<뷰명>') IS NULL OR != '*'`) 을 그대로 확인.
3. **Redacted columns** — `REDACTION_COLUMNS` 에서 어느 컬럼(`EMAIL`, `FULL_NAME`)이 어떤 정규식(`^(.)(.*)(@.*)$` → `\1****\3` 등)으로 마스킹되는지 표시.
4. **Permission summary** — `app_user / user_group / app_group / permission / db_source` 조인으로 “누가 어느 그룹에 속해 어느 소스의 어느 뷰에서 어느 region 까지 보는지” 가 한눈에.

즉 이 한 스크립트가 행 단위(VPD) + 컬럼 단위(Redaction) + 권한 모델(permission 테이블) 세 계층을 모두 들여다봅니다.

---

## 5. 실제 동작 결과 (테스트 출력)

### 5-1. VPDUSER_A — KR 분석가, APAC만 허용

```
SQL> SELECT USER, SYS_CONTEXT('VPD_CTX','V_CUSTOMERS_PG') AS regions FROM dual;

USER       REGIONS
---------- -------
VPDUSER_A  APAC

SQL> SELECT customer_id, full_name, email, region FROM admin.v_customers_pg ORDER BY customer_id;

CUSTOMER_ID FULL_NAME    EMAIL                 REGION
----------- ------------ --------------------- ------
          3 A****        a****@example.com     APAC
          5 C****        c****@example.com     APAC
```

이 한 결과에 **두 가지 보호 계층** 이 동시에 보입니다.

1. **행 단위 (VPD)** — 원본은 5건이지만 2건만 보입니다. `region IN ('NA','EMEA')` 행은 VPDUSER_A 의 세션에서는 “존재 자체를 모릅니다.” `WHERE region='NA'` 같은 조건을 걸어도 0건이 나옵니다.
2. **컬럼 단위 (Data Redaction)** — 보이는 2건의 `full_name` 과 `email` 도 원본이 아닙니다. `Alice Kim` 이 `A****`, `alice.kim@example.com` 이 `a****@example.com` 으로 마스킹돼서 내려옵니다. 정규식 마스킹은 ADB 엔진이 컬럼 표시 직전에 적용하므로, 클라이언트 어디서도 원본 값을 받아볼 수 없습니다.

### 5-2. VPDUSER_B — 글로벌 관리자, 전부 허용

```
SQL> SELECT customer_id, full_name, email, region FROM admin.v_customers_pg ORDER BY customer_id;

CUSTOMER_ID FULL_NAME      EMAIL                       REGION
----------- -------------- --------------------------- ------
          1 John Doe       john.doe@example.com        NA
          2 Jane Smith     jane.smith@example.com      EMEA
          3 Alice Kim      alice.kim@example.com       APAC
          4 Bob Johnson    bob.j@example.com           NA
          5 Charlie Lee    charlie.lee@example.com     APAC
```

같은 SQL, 다른 결과.

- **행 5건 다 보임** — VPDUSER_B 는 `allowed_regions='*'` 이므로 VPD 가 행 필터를 붙이지 않습니다.
- **컬럼도 원본 그대로** — 같은 `*` 권한이 Data Redaction 게이팅 식 (`SYS_CONTEXT('VPD_CTX','V_CUSTOMERS_PG') != '*'`) 을 통과하지 못하므로 마스킹이 작동하지 않고 원본을 그대로 반환합니다.

**같은 한 줄의 SQL 이 사용자 신원만 다를 뿐, 보이는 행 수도 다르고 PII 값도 다릅니다.** 이것이 VPD + Data Redaction 이 ADB 엔진 안에서 동작한 증거입니다.

### 5-3. 우회 시도 — 전부 실패

VPDUSER_A 가 시도해 본 5 가지 우회법은 모두 막혔습니다.

| 시도 | 결과 |
|---|---|
| ① 원본 외부 테이블 직접 조회 (`SELECT FROM ...@RDS_POSTGRES_LINK`) | ❌ `ORA-02019` (DB Link 자체를 모름) |
| ② 컨텍스트 위조 (`DBMS_SESSION.SET_CONTEXT(...)` 직접 호출) | ❌ `ORA-01031: insufficient privileges` |
| ③ VPD 정책 삭제 (`DBMS_RLS.DROP_POLICY`) | ❌ `PLS-00201: DBMS_RLS must be declared` (실행 권한 없음) |
| ④ 권한 테이블 조회 (`SELECT FROM admin.permission`) | ❌ `ORA-00942: table or view does not exist` |
| ⑤ 사용자 테이블 조회 (`SELECT FROM admin.app_user`) | ❌ `ORA-00942` |

---

## 6. “정말로 못 우회하나?” — 보안 보장 7 계층

VPD는 “Oracle 안에서” 강력하지만, “Oracle 밖” 까지 보장해 주진 않습니다. 다음 7 가지 장치가 함께 있어야 빈틈없이 막힙니다. 각 항목은 “만약 이걸 안 해두면 어떻게 뚫리나” 의 시나리오와 함께 설명합니다.

### 6-1. LOGON 트리거 — 항상 컨텍스트가 세팅된다

**뚫리는 시나리오 (장치가 없다면)**: 사용자가 컨텍스트를 안 채우고 그냥 쿼리. 정책 함수가 `NULL` 을 반환해서 “필터 없음” 으로 해석되면 전부 보임.
**막는 방법**: 로그인하는 그 순간 DB 엔진이 트리거를 강제로 실행. 사용자가 SQL을 한 줄도 치기 전에 컨텍스트가 채워진다. 클라이언트가 SQL\*Plus든 JDBC든 ORDS든 상관없이 적용.

### 6-2. Fail-Closed 정책 — 컨텍스트 없으면 “0행”

**뚫리는 시나리오 (장치가 없다면)**: 어떤 이유로 트리거가 실패하면 컨텍스트가 비어 있고, 정책이 “필터 없음 = 전부” 로 해석.
**막는 방법**: 정책 함수가 컨텍스트에 권한이 없으면 무조건 `1=0` 을 반환해서 행 0개. 사고 시 데이터가 새 나가지 않고 그냥 “안 보임” 으로 안전하게 실패한다.

### 6-3. Secure Application Context — 위조 불가

**뚫리는 시나리오 (장치가 없다면)**: 사용자가 `DBMS_SESSION.SET_CONTEXT('VPD_CTX','V_CUSTOMERS_PG','*')` 한 줄로 자기 권한을 무한대로 만든다.
**막는 방법**: `CREATE CONTEXT vpd_ctx USING ctx_pkg`. 이 컨텍스트는 오직 `ctx_pkg` 패키지 안에서 호출되는 SET_CONTEXT만 받는다. 사용자가 직접 호출하면 `ORA-01031` 로 거절.

### 6-4. 최소 권한 원칙 — 뷰 외에는 못 만진다

**뚫리는 시나리오 (장치가 없다면)**: 사용자가 권한 테이블을 직접 UPDATE 해서 자기 그룹의 `allowed_regions` 를 `*` 로 바꿔버린다.
**막는 방법**: 사용자에게 **테이블 조회·수정·정책 변경 권한을 일체 주지 않는다.** 오직 뷰의 SELECT 권한과 `ctx_pkg` 실행 권한만.

### 6-5. 외부 데이터 직접 접근 차단

**뚫리는 시나리오 (장치가 없다면)**: 사용자가 `SELECT * FROM customers@RDS_LINK` 를 직접 쳐서 VPD가 안 걸린 원본을 들여다본다.
**막는 방법**: DB Link 는 ADMIN 의 **PRIVATE DB Link**. 다른 사용자에게 사용 권한을 주지 않으면 그 이름 자체를 알 수 없고 (`ORA-02019: connection description not found`) 호출도 불가.

### 6-6. EXEMPT ACCESS POLICY 권한 차단

**뚫리는 시나리오 (장치가 없다면)**: 어떤 사용자에게 DBA 권한을 잘못 줘서 `EXEMPT ACCESS POLICY` 시스템 권한이 따라간다. 이 권한이 있으면 VPD가 통째로 무시된다.
**막는 방법**: 일반 사용자에겐 `DBA`, `PDB_DBA` 같은 묶음 권한을 **절대 부여하지 않는다.** POC에서는 `CREATE SESSION` 외에 ROLE 자체를 안 줬다.

### 6-7. 정책 조작 권한 차단

**뚫리는 시나리오 (장치가 없다면)**: 사용자가 `DBMS_RLS.DROP_POLICY('ADMIN','V_CUSTOMERS_PG','...')` 을 호출해서 정책을 끈다.
**막는 방법**: `DBMS_RLS` 패키지에 대한 EXECUTE 권한을 주지 않는다. 시도하면 `PLS-00201: identifier 'DBMS_RLS' must be declared` 로 거절.

---

## 7. 추가 강화 옵션 (운영 단계용)

POC 에서는 아래 옵션을 **문서화만** 해 둡니다. 실제 운영 환경에서는 다음 중 필요한 것을 추가하시면 됩니다.

### 7-1. LOGON 트리거를 “Hard-Fail” 모드로 바꾸기

지금은 트리거 안에 `EXCEPTION WHEN OTHERS THEN NULL` 이 있어서, 컨텍스트 로딩이 실패해도 로그인 자체는 성공합니다 (대신 정책이 fail-closed 라 데이터는 안 보임).

운영 환경에선 “문제가 있으면 아예 로그인 자체를 거부” 하는 편이 **모니터링상 명확** 합니다. 다음과 같이 바꿉니다.

```sql
CREATE OR REPLACE TRIGGER vpd_logon_trg
AFTER LOGON ON DATABASE
BEGIN
  IF SYS_CONTEXT('USERENV','SESSION_USER') IN ('VPDUSER_A','VPDUSER_B') THEN
    admin.ctx_pkg.init;
    -- 컨텍스트가 비어 있으면 로그인 거부
    IF SYS_CONTEXT('VPD_CTX','USER_ID') IS NULL THEN
      RAISE_APPLICATION_ERROR(-20001,
        'VPD context could not be loaded. Login blocked for safety.');
    END IF;
  END IF;
END;
/
```

이러면 트리거가 실패하거나 컨텍스트가 비어 있을 때 사용자는 **연결 자체가 안 됩니다.** “행이 안 보인다”가 아니라 “들어올 수 없다” 가 되므로 보안 사고 감지가 빨라집니다.

### 7-2. Proxy Authentication (대리 인증)

웹 애플리케이션, ORDS, APEX 등에서 자주 만나는 함정은 다음과 같습니다.

> 앱 서버가 **항상 ADMIN 계정으로** ADB에 접속한 뒤, “지금 로그인한 사용자는 김철수입니다” 같은 정보를 앱 코드 안에서만 들고 있는 경우. → ADB 입장에서 세션은 ADMIN 이므로 LOGON 트리거가 김철수 용으로 발동하지 않고, VPD가 김철수의 권한을 알 수가 없음. **모든 데이터가 다 보임.**

이를 막으려면 **Proxy Authentication** 을 씁니다.

```sql
-- 미리 한 번:
ALTER USER vpduser_a GRANT CONNECT THROUGH app_user;
ALTER USER vpduser_b GRANT CONNECT THROUGH app_user;
```

이제 앱 서버는 `app_user[vpduser_a]/password@db` 같은 식으로 접속합니다.
- 앱 서버는 vpduser_a 의 비밀번호를 모르고도,
- ADB 안에서 세션의 정체는 vpduser_a 가 되며,
- LOGON 트리거가 vpduser_a 용으로 정상 발동하고,
- VPD가 정상 동작합니다.

ORDS, APEX, JDBC 풀, Python 앱 모두 이 패턴을 지원합니다.

### 7-3. Unified Audit — 누가 무엇을 시도했는지 기록

VPD 가 막아도, “누가 막혔는지” 를 기록해두면 침입 시도 감지나 사고 조사에 좋습니다. ADB는 기본적으로 Unified Audit이 켜져 있고, 다음 같은 조건으로 감사 정책을 추가할 수 있습니다.

```sql
CREATE AUDIT POLICY vpd_view_access
  ACTIONS SELECT ON admin.v_customers_pg,
          SELECT ON admin.v_customers_my;
AUDIT POLICY vpd_view_access;
```

이후 `UNIFIED_AUDIT_TRAIL` 뷰에서 누가 언제 어떤 뷰를 조회했는지 다 보입니다.

### 7-4. 권한 변경의 즉시 반영

지금 구조에서 `permission` 테이블을 바꾸면, 이미 로그인되어 있는 사용자의 세션 컨텍스트는 **그대로** 입니다. 다음 로그인부터 적용됩니다. 즉시 반영이 필요하면 두 가지 방법이 있습니다.

1. **세션 강제 종료**: ADMIN 이 변경 후 그 사용자 세션을 `ALTER SYSTEM KILL SESSION` 으로 끊는다. 다음 로그인 때 새 권한 적용.
2. **정책 함수를 컨텍스트가 아닌 테이블 직접 조회로 작성**: 매 쿼리마다 `permission` 테이블을 읽으니 즉시 반영. **성능 비용** 이 큼 (POC 에서 피한 이유).

### 7-5. Materialized View / 결과 캐시 (성능 최적화)

지금 POC 는 “일단 동작” 에 초점이 있어, DB Link 너머에서 데이터를 다 가져온 뒤 ADB 가 필터링합니다. 큰 테이블에서는 느립니다. 운영 단계에서 가능한 최적화 옵션:

- **Materialized View**: 외부 데이터를 주기적으로 ADB 안으로 복제하고 그 위에 VPD 적용. 사용자는 ADB 안의 사본만 조회 → DB Link 가 매번 안 일어남.
- **Result Cache**: 같은 쿼리 결과를 메모리에 캐싱.
- **Predicate Pushdown**: 정책이 만든 WHERE 절을 외부 DB 쪽으로 밀어 보내기. 이종 DB(MySQL/Postgres) 에선 제한적.

---

## 8. 현재 POC의 한계와 운영 시 고려사항

| 항목 | 현재 POC 상태 | 운영 시 권장 |
|---|---|---|
| 정책 평면 스키마 | `ADMIN` 한 곳에 다 있음 | `VPD_OWNER` 같은 별도 스키마로 분리, ADMIN과 격리 |
| 패키지 권한 | `AUTHID DEFINER` | 그대로 (정책 함수도 DEFINER 가 맞음) |
| 권한 테이블 변경 즉시 반영 | 다음 로그인부터 적용 | 7-4 참조 |
| 성능 (DB Link) | Predicate pushdown 거의 안 됨 | 7-5 참조 |
| 사용자 비밀번호 관리 | 평문 비밀번호 | IAM 또는 Proxy Auth |
| 컨텍스트 위조 | 차단됨 (Secure Context) | 그대로 |
| 외부 DB 직접 접근 | DB Link 사용 권한 없어서 차단 | + 네트워크 단에서 RDS에 직접 접근 못 하도록 보안그룹 잠금 |
| 정책 조작 | 막혀 있음 | 그대로 |
| 감사 로깅 | 없음 | Unified Audit 추가 (7-3 참조) |
| LOGON 실패 정책 | Soft-fail (들어오긴 하나 데이터 0건) | Hard-fail (7-1 참조) |
| 정책 우회 위험성 검토 | 5 가지 시도 모두 차단 확인 | 신규 객체 추가할 때마다 동일 검증 절차 필요 |

---

## 9. 디렉터리·파일 구조

```
vpd-permission-poc/
├── .env                      ← ADB + RDS 연결 정보 (.gitignore 됨)
├── .env.example
├── README.md                 ← 클론-앤-고 가이드
├── run.sh                    ← 원클릭 오케스트레이터
├── scripts/lib/common.sh
├── sql/
│   ├── source/
│   │   ├── postgres_setup.sql   ← 원격 PG: customers + 12 rows
│   │   └── mysql_setup.sql      ← 원격 MySQL: customers + 12 rows
│   └── adb/
│       ├── 00_cleanup.sql       ← 멱등 초기화
│       ├── 01_dblinks.sql       ← DB Link + credential
│       ├── 02_perm_tables.sql   ← 권한 매핑 6개 테이블
│       ├── 03_seed.sql          ← 4-user 매트릭스 시드
│       ├── 04_secure_ctx.sql    ← ctx_pkg + Secure Context
│       ├── 05_views.sql         ← DB Link 통합 뷰
│       ├── 06_policy.sql        ← VPD 정책 + 정책 함수
│       ├── 06a_redaction.sql    ← Data Redaction (이메일/이름 마스킹)
│       ├── 07_end_users.sql     ← 4 유저 + LOGON 트리거
│       ├── 08_tests_user_my.sql    ← MY only + 우회 시도 5종
│       ├── 09_tests_user_pg.sql    ← PG only
│       ├── 10_tests_user_both.sql  ← both
│       ├── 11_tests_user_none.sql  ← default deny 검증
│       └── 12_tests_admin_audit.sql
└── docs/
    ├── 01-quickstart.md
    ├── 02-architecture.md
    └── 03-detailed-guide.md     ← (이 문서)
```

---

## 10. 실행 방법

```bash
cd vpd-permission-poc

# 1) 전체 파이프라인 한 번에
./run.sh                # = ./run.sh all

# 또는 단계별로:
./run.sh prereq         # 도구/.env 검증
./run.sh source         # 원격 PG + MySQL 에 customers 테이블/seed
./run.sh adb            # ADB 측 cleanup → dblink → 권한/뷰/정책 → 4 유저
./run.sh tests          # 4 명 (MY/PG/BOTH/NONE) 시점 테스트
./run.sh audit          # ADMIN 시점 정책·권한 감사
./run.sh teardown       # ADB 측 객체 + dblink 정리
```

### 사람의 눈으로 직접 확인하고 싶을 때

```bash
source .env

# 기본 시나리오 — vpduser_pg: PG 뷰는 다 보이고 MySQL 뷰는 0 rows
sqlplus "vpduser_pg/\"${VPDUSER_PG_PASSWORD}\"@$ADB_TNS"
SQL> SELECT COUNT(*) FROM admin.v_customers_pg;   -- 12
SQL> SELECT COUNT(*) FROM admin.v_customers_my;   -- 0

# vpduser_none — default deny
sqlplus "vpduser_none/\"${VPDUSER_NONE_PASSWORD}\"@$ADB_TNS"
SQL> SELECT COUNT(*) FROM admin.v_customers_pg;   -- 0
SQL> SELECT COUNT(*) FROM admin.v_customers_my;   -- 0

# region 필터 변형을 켰을 때 — vpduser_both 에게 APAC 만 허용한 경우
# (sql/adb/03_seed.sql 하단 UPDATE 주석 해제 후)
sqlplus "vpduser_both/\"${VPDUSER_BOTH_PASSWORD}\"@$ADB_TNS"
SQL> SELECT region, COUNT(*) FROM admin.v_customers_pg GROUP BY region;
-- 결과: APAC 만 보임
```

---

## 11. 데이터 카탈로그 인프라로서의 가능성 — Databricks Unity Catalog 와 비교

이 POC 를 단순히 “권한 관리” 가 아니라 **데이터 카탈로그 플랫폼의 기반(인프라)** 으로 확장할 수 있을까? 결론부터 말씀드리면:

> **“네, 가능합니다. 그리고 사실 ADB 자체에 데이터 카탈로그 기능이 이미 들어 있기 때문에, 우리가 만든 VPD 권한 계층과 결합하면 별도 제품 없이도 의미 있는 카탈로그 플랫폼이 됩니다.”**

### 11-1. ADB 가 “기본 탑재” 로 제공하는 카탈로그 기능들

ADB 의 **Database Actions / Data Studio** 콘솔 안에는 별도 라이선스 없이 쓸 수 있는 데이터 카탈로그 도구들이 포함되어 있습니다.

| 기능 | 어디에서 제공되나 | 무엇을 해 주나 |
|---|---|---|
| **Data Catalog** (Data Studio 안의 도구) | Database Actions → Data Studio → Catalog | 객체(Entity) 등록, 비즈니스 모델, 용어집(Term Glossary), 기본 데이터 계보(lineage) |
| **Sensitive Data Discovery (SDD)** | Data Studio → Data Analysis | 스키마를 스캔해서 PII(개인정보) 컬럼을 자동 탐지·태깅 |
| **Data Profiling** | Data Studio → Data Insights | 컬럼 통계, 분포, NULL 비율, 이상치 등 |
| **Business Glossary / Terms** | Data Catalog 안 | 비즈니스 용어를 데이터 객체와 매핑 |
| **Data Dictionary** | `ALL_TABLES`, `ALL_TAB_COLUMNS` 등 | 모든 카탈로그 도구의 기반이 되는 메타데이터 |
| **ORDS (REST 자동 노출)** | Database Actions → REST | 객체에 REST 엔드포인트를 자동 부여 |
| **APEX** | Database Actions → APEX | 권한·메타데이터 위에 노코드 UI 구축 |
| **Unified Audit** | DB 엔진 내장 | 모든 접근·실패 시도 감사 로그 |

즉, “카탈로그” 라는 말이 의미하는 대부분의 기능(탐색·분류·민감정보 태깅·프로파일링·용어집·기본 계보·감사) 이 **이미 ADB 안에 있습니다.** 우리가 새로 만든 것은 그 위에서 “행 단위 권한을 자동으로 적용하는 집행 계층” 입니다.

### 11-2. Unity Catalog 가 제공하는 것 vs. 우리 ADB + VPD 가 제공하는 것

| 기능 | ADB 기본 + 우리 POC | Unity Catalog | 메모 |
|---|---|---|---|
| 단일 정책 평면 (중앙 집중 권한 관리) | ✅ (POC 가 추가) | ✅ | 동등 |
| Row-level Security (행 단위 권한) | ✅ (POC: VPD) | ✅ | 동등 |
| Column-level Masking (컬럼 단위 마스킹) | ✅ (POC: Oracle Data Redaction, `email`/`full_name` 마스킹) | ✅ | 동등 |
| 이종(Multi-source) DB 통합 | ✅ DB Link / External Tables | ✅ External Location | 동등 |
| 사용자 신원 기반 집행 | ✅ LOGON + Context | ✅ 컴퓨트 계층 | 동등 |
| 메타데이터 카탈로그 (테이블·소유자·태그) | ✅ **Data Studio Catalog 내장** | ✅ | 동등 |
| 탐색 UI / 검색 | ✅ **Database Actions 내장** | ✅ | 동등 |
| 비즈니스 용어집 (Glossary) | ✅ **Data Catalog Terms 내장** | ✅ | 동등 |
| 민감정보 자동 분류 / 태깅 | ✅ **Sensitive Data Discovery 내장** | ✅ | 동등 |
| 데이터 프로파일링 | ✅ **Data Insights 내장** | ✅ | 동등 |
| 감사 로그 | ✅ Unified Audit 내장 | ✅ | 동등 |
| 기본 데이터 계보 (Lineage) | ⚠️ ADB 내부 한정 | ✅ 폭넓음 | 부분 동등 |
| 조직 간 데이터 공유 | ⚠️ Oracle Data Share 있으나 범위 좁음 | ✅ Delta Sharing | 약점 |
| **SQL 이외 경로(예: Spark on S3) 의 통제** | ❌ ADB 를 거치지 않으면 무력 | ✅ Databricks 컴퓨트 거치면 통제됨 | **이게 가장 큰 차이점** |

### 11-3. 가장 중요한 차이점 — “어디서” 정책이 집행되는가

**Unity Catalog 의 강점**: 정책을 **컴퓨트 계층(Spark, SQL Warehouse)** 에서 강제합니다. 즉, Databricks 에서 돌아가는 Spark 작업이 S3 의 Parquet 파일을 직접 읽어도 그 작업이 Databricks 컴퓨트 위에서 실행되는 한 정책이 적용됩니다.

**ADB+VPD 의 강점**: 정책을 **데이터베이스 내부(SQL parser)** 에서 강제합니다. 어떤 클라이언트(BI 도구, JDBC, ORDS, sqlplus...)에서 들어오든, **ADB 를 통해 들어오는 모든 쿼리는 무조건 막힙니다.** “정책을 호출하지 않으면 그만” 같은 회피가 불가능합니다.

**그렇다면 한계는?** ADB 는 “ADB 를 거치지 않는 접근” 은 막을 수 없습니다. 누군가 Spark 작업을 짜서 RDS 에 직접 붙으면 ADB 에는 아무 흔적이 안 남습니다. 이 빈틈은 두 가지로 메웁니다.

1. **아키텍처 규칙**: “이 데이터 소스들에 대한 모든 접근은 ADB 를 통해야 한다” 를 조직 규칙으로 못 박는다.
2. **네트워크 통제**: RDS 인스턴스를 외부에서 직접 못 부르도록 보안그룹/VPC 단에서 잠근다. 오직 ADB 만이 RDS 까지 도달 가능하게.

기술적 보장이 아니라 **설계 결정**입니다. 충분히 달성 가능하지만 기술이 자동으로 막아주는 게 아니라는 점을 인지하셔야 합니다.

### 11-4. ADB+VPD 를 카탈로그 인프라로 쓸 때의 진짜 강점

1. **집행이 수학적으로 보장된다.** VPD 는 SQL 파서 단계에서 적용됩니다. 어떤 클라이언트도, 어떤 BI 도구도, 어떤 드라이버도 이 정책을 건너뛸 수 없습니다. 앱 코드가 “정책 함수 호출하는 걸 깜빡했어요” 같은 사고가 원천 차단됩니다.
2. **데이터 이동이 필요 없다.** Unity Catalog 의 진가는 데이터가 Databricks 의 Delta Lake 안에 있을 때 발휘됩니다. ADB+VPD 는 **MySQL 은 MySQL 인 채로, Postgres 는 Postgres 인 채로** 통합 거버넌스를 줍니다.
3. **다종(polyglot) RDBMS 환경에 강하다.** 이종 DB Link 와 Database Gateway 로 Oracle + MySQL + Postgres + SQL Server + Db2 등을 한 시야에서 묶을 수 있습니다.
4. **SQL 네이티브.** Oracle Net 이나 JDBC 만 말할 줄 알면 어떤 BI 도구든 그대로 붙습니다. 별도 커넥터를 만들 필요가 없습니다.
5. **규제 산업 (금융·보험·의료) 에 매우 적합합니다.** “페타바이트 스케일” 보다 “감사 추적과 증명 가능한 집행” 이 더 중요한 영역에서 이 구조는 자연스럽습니다.

### 11-5. ADB+VPD 가 적합하지 않은 경우

- **레이크하우스 / 대용량 객체 스토리지 분석 (TB ~ PB 단위 스캔, ML 파이프라인 등).** 이종 DB Link Gateway 는 Predicate Pushdown 이 제한적이라, 큰 결과를 다 끌어와 로컬에서 필터링하는 일이 생깁니다.
- **이미 Spark 중심으로 굴러가는 조직.** 사용자가 굳이 ADB 를 거치지 않으려 들 가능성이 높습니다.
- **멀티 클라우드·멀티 계정 거버넌스가 이미 Unity / Lake Formation / Atlan 등으로 정착된 환경.**

### 11-6. 카탈로그 플랫폼으로 진화시킬 때의 권장 아키텍처 (수정판)

ADB 안에 이미 카탈로그 도구들이 들어 있다는 점을 반영하면 그림이 한층 단순해집니다. **별도 제품을 사 붙일 필요 없이 ADB 한 통 안에서 끝납니다.**

```
┌──────────────────────────────────────────────────────────────────┐
│                            ADB (단일 박스)                         │
│                                                                    │
│   ① 탐색 / 메타데이터 / UI    (전부 ADB 내장)                       │
│      ├─ Database Actions (웹 콘솔)                                 │
│      ├─ Data Studio → Data Catalog (Entity, Glossary, Lineage)     │
│      ├─ Data Studio → Sensitive Data Discovery (PII 자동 태깅)     │
│      ├─ Data Studio → Data Insights (프로파일링)                   │
│      ├─ APEX (권한 관리 UI 노코드 구축)                            │
│      └─ ORDS (모든 객체 REST 자동 노출)                            │
│                                                                    │
│   ② 정책 평면    (오늘의 POC 가 추가한 부분)                       │
│      ├─ permission / group / user / source 테이블                  │
│      ├─ Secure Application Context                                 │
│      └─ Unified Audit (집행 결과·실패 시도 기록)                   │
│                                                                    │
│   ③ 집행    (ADB 엔진 안에서 자동 적용)                            │
│      ├─ VPD 정책 (행 단위)                                         │
│      └─ Data Redaction (컬럼 단위, PII 마스킹) — 구현됨           │
│                                                                    │
│   ④ 데이터 소스                                                   │
│      ├─ ADB 로컬 테이블 / 외부 테이블 (DBMS_CLOUD)                 │
│      └─ DB Link 로 묶인 RDS MySQL / Postgres / 다른 Oracle 등      │
└──────────────────────────────────────────────────────────────────┘
                              ▲
                              │ 모든 SQL 클라이언트 (BI, JDBC, ORDS, sqlplus...)
```

이 그림이 보여 주는 핵심은: **카탈로그 + 집행 + 감사 + UI 가 한 박스 안에 있다.** Unity Catalog 처럼 별도 컨트롤 플레인을 구입·설치할 필요가 없습니다.

### 11-7. POC 를 “진짜 카탈로그 시나리오” 로 확장하기

지금 POC 에 ADB 내장 카탈로그 기능을 결합하면 다음과 같은 **엔드-투-엔드 시나리오** 가 자연스럽게 완성됩니다. 이게 “카탈로그 + 거버넌스” 플랫폼이 실제 운영에서 어떻게 돌아가는지를 보여 주는 모습입니다.

#### 시나리오: PII 자동 인식 → 자동 마스킹 + 행 권한

1. **데이터 등록 (Data Studio Catalog)**
   데이터 엔지니어가 `v_customers_pg`, `v_customers_my` 를 Data Catalog 에 “Customer” 라는 비즈니스 Entity 로 등록합니다. 용어집에 “Customer”, “Region” 같은 비즈니스 용어를 정의하고 매핑합니다.

2. **민감정보 자동 탐지 (Sensitive Data Discovery)**
   ADB 가 두 뷰를 스캔해서 `email`, `full_name` 컬럼을 **PII** 로 자동 태깅합니다. 사람이 컬럼 하나하나 확인할 필요가 없습니다.

3. **정책 정의 (우리의 권한 모델 + Oracle Data Redaction)**
   관리자가 카탈로그 UI 에서 결정합니다:
   - **행 단위 (VPD)**: KR_ANALYSTS → APAC 만 — **구현됨**
   - **컬럼 단위 (Data Redaction)**: PII 로 태깅된 컬럼은 KR_ANALYSTS 에게는 마스킹(`a****@example.com`, `A****`), GLOBAL_ADMINS 에게는 원본 그대로 — **구현됨** (`sql/05a_redaction.sql`)

4. **사용자 질의 — 모든 통제가 자동 적용**
   VPDUSER_A 가 `SELECT * FROM v_customers_pg` 한 줄을 던지면:
   - VPD 가 `region IN ('APAC')` 을 자동으로 결합 → 행 필터링
   - Data Redaction 이 `email`, `full_name` 을 마스킹 → 컬럼 보호
   - 두 정책 모두 사용자가 알지 못한 채 적용됨

5. **감사 (Unified Audit)**
   누가 언제 어떤 뷰를 조회했고, 어떤 정책이 적용됐고, 거부된 시도는 무엇이었는지 모두 기록됩니다.

6. **거버넌스 운영 (APEX UI)**
   비-DBA 인 데이터 거버넌스 담당자가 APEX 로 만들어진 화면에서 권한을 부여/회수합니다. SQL 을 한 줄도 안 씁니다.

이 흐름이 완성되면 **“우리 회사 데이터 카탈로그 플랫폼” 이라는 표현이 정당화** 됩니다.

#### 카탈로그 시나리오 확장 시 작업 우선순위

1. ~~**Data Redaction 정책 추가**~~ — **완료.** `sql/05a_redaction.sql` 에서 `email`, `full_name` 을 정규식 기반으로 마스킹. 정책 식은 `SYS_CONTEXT('VPD_CTX','<뷰명>') != '*'` 일 때만 마스킹 적용.
2. **Data Studio Catalog 에 두 뷰 등록 + Term Glossary 정의** — UI 작업, 코딩 거의 없음.
3. **Sensitive Data Discovery 실행** — 한 번의 클릭으로 PII 자동 태깅.
4. **APEX 권한 관리 앱** — `permission` 테이블 위에 노코드 양식 폼 구축. (1~2일)
5. **Unified Audit 정책 활성화 + 대시보드** — 감사 로그를 운영자가 볼 수 있게.
6. **OCI Data Catalog 와 연계 (선택)** — 조직 전체의 다른 데이터 자산(데이터레이크, S3, OCI Object Storage 등)까지 시야를 넓힐 때.

### 11-8. 결론

| 질문 | 답 |
|---|---|
| ADB + VPD 를 “데이터 카탈로그 플랫폼” 이라고 말할 수 있는가? | **네, 정당화 가능합니다.** Database Actions / Data Studio 안에 이미 카탈로그 도구(Catalog, SDD, Insights, Glossary) 가 내장되어 있고, 우리 POC 가 추가한 권한 집행 계층이 거버넌스의 마지막 퍼즐을 채웁니다. |
| Unity Catalog 와 동등한 제품이 되는가? | **포지셔닝이 다릅니다.** Unity 는 “레이크하우스 + Spark 컴퓨트” 중심, 우리 구조는 “RDBMS·SQL 중심 거버넌스 + 집행 플랫폼”. 각각 잘 맞는 영역이 다릅니다. |
| 가장 큰 약점은? | ADB 를 거치지 않는 경로(예: Spark → RDS 직결, 객체 스토리지 직접 분석) 에 무력. 네트워크·아키텍처 정책으로 그 빈틈을 메워야 합니다. 계보(lineage) 도 ADB 가 보는 범위로 제한됩니다. |
| 가장 큰 강점은? | 집행이 SQL 파서 안에서 일어나서 **앱 코드가 절대 우회할 수 없습니다.** 그리고 **모든 카탈로그 기능이 한 박스(ADB) 안에 있어** 별도 제품 도입·통합 비용이 없습니다. |
| 다음 단계로 추천하는 것? | 11-7 의 6단계 — Data Redaction 은 이미 구현됨. 남은 항목 중 (1) Data Catalog 등록 + SDD (2) APEX 권한 관리 UI 까지만 추가해도 “카탈로그 플랫폼” 이라고 부르기 충분한 형태가 됩니다. |

---

## 마무리

이 POC가 보여주는 핵심은 단 하나입니다.

> **권한 정책을 데이터베이스 한 곳에 모아두고, 사용자의 SQL은 그대로 두면서 Oracle이 자동으로 알맞게 필터링한다.**

사용자는 `WHERE` 절을 매번 쓸 필요가 없고, 개발자는 권한 로직을 앱 코드에 흩뿌릴 필요가 없고, 보안 담당자는 한 곳만 보면 누가 어디까지 보는지 알 수 있습니다. 그리고 위 6번에서 본 7 가지 계층이 함께 있어, “VPD 만 믿었는데 우회됐다” 같은 시나리오는 발생하기 어렵습니다.

운영 단계로 넘어갈 때는 7번 절의 강화 옵션과 8번 절의 한계 표를 점검 리스트로 활용하시면 됩니다.
