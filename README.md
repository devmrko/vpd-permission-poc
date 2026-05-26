# VPD Permission POC — ADB 중심 행 단위 접근 제어

Oracle Autonomous Database (26ai) 의 **VPD (Virtual Private Database)** 와
**Data Redaction** 을 이용해, ADB 한 곳에서 여러 원본 DB (AWS RDS for PostgreSQL,
MySQL) 의 데이터를 **유저 → 그룹 → 권한 매핑 테이블** 만으로 행/열 단위 통제하는
End-to-End 데모입니다.

> 핵심: 원본 DB 는 단순한 데이터 소스로 두고, 접근 권한은 ADB 의 VPD 정책 함수와
> Secure Application Context 로 통합 관리합니다. 애플리케이션 코드 변경 없이
> 로그인하는 DB 유저에 따라 자동으로 행이 필터링됩니다.

---

## 구성요소

| 계층 | 객체 | 역할 |
|---|---|---|
| 원격 | `public.customers` (PG), `ecommerce_poc.customers` (MySQL) | 원본 데이터 |
| ADB - 연결 | `RDS_POSTGRES_LINK`, `RDS_LINK` | `DBMS_CLOUD_ADMIN.CREATE_DATABASE_LINK` 로 만든 heterogeneous DB Link |
| ADB - 매핑 | `app_customer`, `app_user`, `app_group`, `user_group`, `db_source`, `permission` | 누가 어느 소스의 어느 region 을 볼 수 있는지 |
| ADB - 컨텍스트 | `vpd_ctx` (Secure Application Context) + `ctx_pkg` | 로그인 시 권한을 세션 컨텍스트로 로딩 |
| ADB - 뷰 | `v_customers_pg`, `v_customers_my` | DB Link 위의 통합 뷰. **이 뷰에만 VPD/Redaction 정책이 붙음** |
| ADB - 정책 | `CUSTOMERS_PG_POLICY`, `CUSTOMERS_MY_POLICY`, `PII_REDACT_PG/MY` | VPD 행 필터 + 이메일 마스킹 |
| ADB - 엔드유저 | `vpduser_my`, `vpduser_pg`, `vpduser_both`, `vpduser_none` | 최소 권한. 뷰만 SELECT 가능. LOGON 트리거로 컨텍스트 자동 로딩 |

---

## 빠른 시작 (One-Click)

```bash
git clone https://github.com/<you>/vpd-permission-poc.git
cd vpd-permission-poc

# 1) 환경값 채우기
cp .env.example .env
$EDITOR .env

# 2) 끝
./run.sh
```

`./run.sh` 가 다음을 순서대로 실행합니다:

1. **prereq** — `sqlplus`, `psql`, `mysql` 존재 + `.env` 변수 검증
2. **source** — 원격 PG/MySQL 에 `customers` 테이블 + seed (멱등)
3. **adb** — ADB 측 cleanup → DB Link → 권한 테이블/seed → context/view/policy → 엔드유저
4. **tests** — 4 명 (`vpduser_my`/`vpduser_pg`/`vpduser_both`/`vpduser_none`) 로 접속해 행 필터/마스킹/default-deny 검증
5. **audit** — ADMIN 으로 정책/뷰/유저 상태 점검

세부 단계만 따로 돌리려면:

```bash
./run.sh prereq      # 환경만 검증
./run.sh source      # 원격 DB 만 준비
./run.sh adb         # ADB 만 준비
./run.sh tests       # 엔드유저 테스트만
./run.sh audit       # ADMIN audit 만
./run.sh teardown    # ADB 측 객체 + DB Link/credential 정리
```

---

## 사전 준비

| 항목 | 필요 사항 |
|---|---|
| ADB | Autonomous Database (Always Free 도 가능). Wallet 다운로드 + Instant Client 에 풀어둠 |
| PostgreSQL | 호스트/포트 도달 가능. `vpdpoc` (혹은 원하는 이름) DB 가 미리 생성되어 있어야 함 |
| MySQL | 호스트/포트 도달 가능. `ecommerce_poc` (혹은 원하는 이름) DB 가 미리 생성되어 있어야 함 |
| 클라이언트 도구 | `sqlplus` (Instant Client), `psql`, `mysql` |

`.env.example` 의 각 항목 의미는 같은 파일 안 주석 참고.

---

## 데모 시나리오 — 2×2 source access matrix

`sql/adb/03_seed.sql` 의 매핑 (4 유저, 4 케이스):

| DB 유저 | 그룹 | PG 뷰 | MySQL 뷰 | VPD 결과 |
|---|---|---|---|---|
| `vpduser_my`   | `MY_ONLY`      | ✗ 0 rows | ✓ ALL    | PG=`1=0` / MY=`NULL` |
| `vpduser_pg`   | `PG_ONLY`      | ✓ ALL    | ✗ 0 rows | PG=`NULL` / MY=`1=0` |
| `vpduser_both` | `BOTH_SOURCES` | ✓ ALL    | ✓ ALL    | PG=`NULL` / MY=`NULL` |
| `vpduser_none` | (그룹 없음)    | ✗ 0 rows | ✗ 0 rows | PG=`1=0` / MY=`1=0` (default deny) |

핵심 포인트:

* 네 유저 모두 **양쪽 뷰에 SELECT GRANT 는 동일하게 주어집니다.** 차이를 만드는 건
  GRANT 가 아니라 `permission` 테이블의 행. 즉 **권한 회수 = GRANT 해제가 아니라
  permission row 삭제**.
* `vpduser_none` 처럼 매핑이 아예 없는 유저는 자동으로 fail-closed
  (`1=0` predicate) — **deny by default**.
* 누구든 원본 테이블 직접 접근 시도 (`@RDS_POSTGRES_LINK` 등) → 권한 없음.

`sql/adb/08_tests_user_my.sql` 가 우회 시도 5개 (원격 직접 SELECT, 컨텍스트
스푸핑, DBMS_RLS 변경, 매핑 테이블 SELECT) 를 시도하고 모두 ORA-xxxxx 로 실패하는 것을
보여줍니다. 09/10/11 은 각 유저의 expected 행 수를 가볍게 확인합니다.

> Row-level region 필터링 (예: `vpduser_both` 가 PG 는 APAC 만) 도 정책 함수에
> 그대로 살아있습니다. `03_seed.sql` 하단의 주석 처리된 `UPDATE permission ...
> allowed_regions='APAC'` 한 줄이면 활성화됩니다.

---

## 디렉토리

```
.
├── run.sh                       # 원클릭 엔트리포인트
├── .env.example
├── scripts/lib/common.sh        # log/ok/warn/die + env 검증 헬퍼
├── sql/
│   ├── source/
│   │   ├── postgres_setup.sql   # 원격 PG: customers + 12 rows
│   │   └── mysql_setup.sql      # 원격 MySQL: customers + 12 rows
│   └── adb/
│       ├── 00_cleanup.sql       # 멱등 teardown
│       ├── 01_dblinks.sql       # DB Link + credential 생성
│       ├── 02_perm_tables.sql   # 6개 권한 매핑 테이블
│       ├── 03_seed.sql          # 데모 매핑 데이터
│       ├── 04_secure_ctx.sql    # ctx_pkg + vpd_ctx (Secure App Context)
│       ├── 05_views.sql         # DB Link 통합 뷰
│       ├── 06_policy.sql        # VPD 정책 + 정책 함수
│       ├── 06a_redaction.sql    # Data Redaction (이메일 마스킹)
│       ├── 07_end_users.sql        # 4 유저 + LOGON 트리거
│       ├── 08_tests_user_my.sql    # MY only — 우회 시도 5종 포함
│       ├── 09_tests_user_pg.sql    # PG only
│       ├── 10_tests_user_both.sql  # both
│       ├── 11_tests_user_none.sql  # default deny (fail-closed) 검증
│       ├── 12_tests_admin_audit.sql
│       └── 13_dds_variant.sql      # (선택) 같은 4-user 매트릭스를 26ai Deep Data Security 로 재구현
└── docs/
    ├── 03-detailed-guide.md     # 한국어 상세 설명 (아키텍처, 정책 로직, 운영 고려사항)
    └── 05-dds-variant.md        # (선택) VPD ↔ DDS 1:1 매핑 + 26ai 변형 사용법
```

---

## 더 깊이 알고 싶으면

* [docs/03-detailed-guide.md](docs/03-detailed-guide.md) — 권한 매핑 모델, 정책
  함수의 동적 SQL, Secure Context 사용 이유, 운영 시 주의점 등 전체 해설.
* [docs/05-dds-variant.md](docs/05-dds-variant.md) — Oracle AI Database 26ai
  의 신기능 **Deep Data Security** 로 같은 데모를 재구현 (선택). 선언형 SQL
  Data Grant 가 VPD 의 PL/SQL 정책 함수를 어떻게 한 줄로 대체하는지 비교.

---

## 라이선스

MIT.
