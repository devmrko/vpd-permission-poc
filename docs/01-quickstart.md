# 01 · Quickstart

`./run.sh` 한 번으로 모든 게 끝납니다. 이 문서는 첫 실행 전에 무엇이 필요하고,
무엇이 잘못될 수 있는지 알려줍니다.

---

## 1. 사전에 준비되어 있어야 하는 것

### 1-1. ADB (Autonomous Database)

* 인스턴스 1개. Always Free 도 OK.
* OCI 콘솔에서 **Wallet 다운로드 → 로컬에 압축 풀기**.
* Instant Client (`sqlplus`) 가 설치되어 있고 PATH 에 등록되어 있을 것.

### 1-2. 원격 Postgres / MySQL

* ADB 가 네트워크로 도달 가능해야 합니다. (RDS public access ON, 보안그룹에
  ADB egress IP 허용 등)
* **DB 가 미리 생성되어 있어야 합니다.** 테이블은 `run.sh source` 가 만듭니다.
  ```sql
  -- Postgres
  CREATE DATABASE vpdpoc;
  -- MySQL
  CREATE DATABASE ecommerce_poc;
  ```
* `psql`, `mysql` 클라이언트가 로컬에 설치되어 있어야 합니다.
  ```bash
  brew install libpq mysql-client       # macOS
  apt-get install postgresql-client mysql-client   # Debian/Ubuntu
  ```

### 1-3. ADB ADMIN 계정 패스워드

* `.env` 의 `ADB_PASSWORD` 에 넣거나, 비워두고 실행 시 프롬프트로 입력.

---

## 2. `.env` 채우기

```bash
cp .env.example .env
$EDITOR .env
```

최소한 다음 항목은 비어 있으면 안 됩니다:

| 변수 | 의미 |
|---|---|
| `TNS_ADMIN` | Wallet 풀린 디렉토리 (cwallet.sso, tnsnames.ora 가 있는 곳) |
| `ADB_TNS` | tnsnames.ora 안의 service alias (예: `d8aukro81636mon0_tp`) |
| `ADB_USER`, `ADB_PASSWORD` | ADB 관리자 계정 |
| `PG_HOST` 등 | 원격 Postgres 연결정보 |
| `MY_HOST` 등 | 원격 MySQL 연결정보 |

VPDUSER (`my` / `pg` / `both` / `none`) 의 패스워드는 데모용 기본값이 들어있으니 그대로
써도 무방하지만, 공유 환경이면 바꿔주세요.

---

## 3. 실행

```bash
./run.sh
```

기대 출력 (요약):

```
[ OK ] prereq 통과
[ OK ] Postgres source 준비 완료
[ OK ] MySQL source 준비 완료
[ OK ] ADB setup 완료
[ OK ] 4명 (MY / PG / BOTH / NONE) 테스트 실행 완료 (위 출력에서 행 수 / 거부 결과 확인)
[ OK ] audit 완료
[ OK ] === ALL DONE — VPD POC 전체 파이프라인 통과 ===
```

이후 직접 검증해보고 싶으면:

```bash
# vpduser_pg 로 접속 → PG 뷰는 전부 보이고 MY 뷰는 0 rows 여야 함
sqlplus vpduser_pg/${VPDUSER_PG_PASSWORD}@${ADB_TNS}
SQL> SELECT COUNT(*) FROM admin.v_customers_pg;   -- 12
SQL> SELECT COUNT(*) FROM admin.v_customers_my;   -- 0

# vpduser_none 으로 접속 → 양쪽 뷰 모두 0 rows (default deny)
sqlplus vpduser_none/${VPDUSER_NONE_PASSWORD}@${ADB_TNS}
SQL> SELECT COUNT(*) FROM admin.v_customers_pg;   -- 0
SQL> SELECT COUNT(*) FROM admin.v_customers_my;   -- 0
```

---

## 4. 자주 하는 실수

| 증상 | 원인 / 해결 |
|---|---|
| `TNS:could not resolve` | `TNS_ADMIN` 경로 잘못 / `ADB_TNS` 가 tnsnames.ora 에 없음 |
| `ORA-12506` | Wallet 풀린 위치는 맞는데 권한 문제 (`chmod 600` 시도 X — 600 이면 sqlplus 가 못 읽음, 644 OK) |
| Postgres 연결 실패 | RDS Public Access 꺼져있거나 SG/firewall 차단. 로컬에서 `psql -h ... -U ...` 로 먼저 확인 |
| `ORA-28000` | ADB 계정이 잠겼음. OCI 콘솔에서 unlock |
| `ORA-65020` (DB Link 생성 실패) | 같은 이름 link 가 이미 다른 host 로 등록돼 있음. `00_cleanup.sql` 부터 다시 |
| 정책이 안 먹는다 (모든 row 보임) | `vpduser_a` 가 아니라 `admin` 으로 접속한 것 — ADMIN 은 정책 BYPASS |
