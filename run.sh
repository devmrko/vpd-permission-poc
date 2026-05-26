#!/usr/bin/env bash
# ============================================================
# run.sh  —  VPD Permission POC 원클릭 오케스트레이터
#
# 사용법:
#   ./run.sh              # = ./run.sh all
#   ./run.sh prereq       # 도구 존재 / .env 변수만 검증
#   ./run.sh source       # 원격 PG + MySQL 에 customers 테이블/seed 생성
#   ./run.sh adb          # ADB 측 cleanup → dblinks → perm/ctx/view/policy → end_users
#   ./run.sh tests        # 4-user (my/pg/both/none) 로 접속해서 행 필터 검증
#   ./run.sh audit        # admin 으로 정책/뷰/유저 상태 점검
#   ./run.sh all          # source → adb → tests → audit
#   ./run.sh teardown     # ADB 측 객체 + 원격 link/cred 만 정리 (원격 PG/MySQL 데이터는 보존)
#
# 환경설정:
#   cp .env.example .env  →  값 채우고  →  ./run.sh
# ============================================================
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

# --- 1) .env 로드 ---
if [[ ! -f "$ROOT/.env" ]]; then
  echo "[FAIL] .env 가 없습니다. cp .env.example .env 후 값을 채워주세요." >&2
  exit 1
fi
# shellcheck disable=SC1091
set -a; . "$ROOT/.env"; set +a

# --- 2) 공용 함수 로드 ---
# shellcheck disable=SC1091
. "$ROOT/scripts/lib/common.sh"

CMD="${1:-all}"

# ============================================================
# 헬퍼: ADB sqlplus 한 번 호출
# 인자: $1 = SQL 파일 경로 (DEFINE 변수는 환경변수에서 합성)
# ADB_PASSWORD 가 비어있으면 prompt.
# ============================================================
run_sqlplus_file() {
  local sql_file="$1"
  [[ -f "$sql_file" ]] || die "SQL 파일 없음: $sql_file"

  log "sqlplus ← $sql_file"
  # heredoc 으로 DEFINE 주입 후 @sql_file 실행.
  # 비번은 stdin 으로 흘려보내고 셸 트레이스/echo 에 안 남게 한다.
  sqlplus -S -L "${ADB_USER}/${ADB_PASSWORD}@${ADB_TNS}" <<SQLEOF
WHENEVER SQLERROR EXIT SQL.SQLCODE
SET DEFINE ON
DEFINE DBLINK_PG_NAME     = "${DBLINK_PG_NAME}"
DEFINE DBLINK_MY_NAME     = "${DBLINK_MY_NAME}"
DEFINE PG_HOST            = "${PG_HOST}"
DEFINE PG_PORT            = ${PG_PORT}
DEFINE PG_DB              = "${PG_DB}"
DEFINE PG_USER            = "${PG_USER}"
DEFINE PG_PASSWORD        = "${PG_PASSWORD}"
DEFINE MY_HOST            = "${MY_HOST}"
DEFINE MY_PORT            = ${MY_PORT}
DEFINE MY_DB              = "${MY_DB}"
DEFINE MY_USER            = "${MY_USER}"
DEFINE MY_PASSWORD        = "${MY_PASSWORD}"
DEFINE VPDUSER_MY_PASSWORD   = "${VPDUSER_MY_PASSWORD}"
DEFINE VPDUSER_PG_PASSWORD   = "${VPDUSER_PG_PASSWORD}"
DEFINE VPDUSER_BOTH_PASSWORD = "${VPDUSER_BOTH_PASSWORD}"
DEFINE VPDUSER_NONE_PASSWORD = "${VPDUSER_NONE_PASSWORD}"
@${sql_file}
SQLEOF
}

# ============================================================
# 헬퍼: 엔드유저 (vpduser_my / pg / both / none) 로 sqlplus 호출
# 인자: $1 = USER, $2 = PASSWORD, $3 = SQL 파일
# ============================================================
run_sqlplus_as() {
  local user="$1" pw="$2" sql_file="$3"
  [[ -f "$sql_file" ]] || die "SQL 파일 없음: $sql_file"

  log "sqlplus(${user}) ← $sql_file"
  sqlplus -S -L "${user}/${pw}@${ADB_TNS}" <<SQLEOF
WHENEVER SQLERROR CONTINUE
@${sql_file}
SQLEOF
}

# ============================================================
# 단계별 함수
# ============================================================
do_prereq() {
  log "=== prereq: 도구 및 환경변수 검증 ==="
  need_cmd sqlplus "Oracle Instant Client (sqlplus) PATH 등록 필요"
  need_cmd psql    "PostgreSQL client (psql) 설치 필요 — brew install libpq 등"
  need_cmd mysql   "MySQL client 설치 필요 — brew install mysql-client 등"

  require_env \
    TNS_ADMIN ADB_TNS ADB_USER \
    VPDUSER_MY_PASSWORD VPDUSER_PG_PASSWORD \
    VPDUSER_BOTH_PASSWORD VPDUSER_NONE_PASSWORD \
    PG_HOST PG_PORT PG_DB PG_USER \
    MY_HOST MY_PORT MY_DB MY_USER \
    DBLINK_PG_NAME DBLINK_MY_NAME

  prompt_password_if_empty ADB_PASSWORD "ADB ADMIN"
  prompt_password_if_empty PG_PASSWORD  "PostgreSQL (${PG_USER}@${PG_HOST})"
  prompt_password_if_empty MY_PASSWORD  "MySQL (${MY_USER}@${MY_HOST})"

  [[ -d "$TNS_ADMIN" ]] || die "TNS_ADMIN 디렉토리가 존재하지 않음: $TNS_ADMIN"
  [[ -f "$TNS_ADMIN/tnsnames.ora" ]] || warn "tnsnames.ora 가 $TNS_ADMIN 에 없음 — 확인 필요"

  ok "prereq 통과"
}

do_source() {
  log "=== source: 원격 Postgres / MySQL 에 customers 테이블 + seed ==="

  log "-- Postgres ($PG_HOST:$PG_PORT/$PG_DB) --"
  PGPASSWORD="$PG_PASSWORD" psql \
    -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$PG_DB" \
    -v ON_ERROR_STOP=1 \
    -f "$ROOT/sql/source/postgres_setup.sql"
  ok "Postgres source 준비 완료"

  log "-- MySQL ($MY_HOST:$MY_PORT/$MY_DB) --"
  mysql \
    -h "$MY_HOST" -P "$MY_PORT" -u "$MY_USER" "-p${MY_PASSWORD}" \
    "$MY_DB" < "$ROOT/sql/source/mysql_setup.sql"
  ok "MySQL source 준비 완료"
}

do_adb() {
  log "=== adb: ADB 측 cleanup → dblinks → perm/ctx/view/policy → end_users ==="
  run_sqlplus_file "$ROOT/sql/adb/00_cleanup.sql"
  run_sqlplus_file "$ROOT/sql/adb/01_dblinks.sql"
  run_sqlplus_file "$ROOT/sql/adb/02_perm_tables.sql"
  run_sqlplus_file "$ROOT/sql/adb/03_seed.sql"
  run_sqlplus_file "$ROOT/sql/adb/04_secure_ctx.sql"
  run_sqlplus_file "$ROOT/sql/adb/05_views.sql"
  run_sqlplus_file "$ROOT/sql/adb/06_policy.sql"
  run_sqlplus_file "$ROOT/sql/adb/06a_redaction.sql"
  run_sqlplus_file "$ROOT/sql/adb/07_end_users.sql"
  ok "ADB setup 완료"
}

do_tests() {
  log "=== tests: 4-user access matrix 검증 ==="
  run_sqlplus_as vpduser_my   "$VPDUSER_MY_PASSWORD"   "$ROOT/sql/adb/08_tests_user_my.sql"
  run_sqlplus_as vpduser_pg   "$VPDUSER_PG_PASSWORD"   "$ROOT/sql/adb/09_tests_user_pg.sql"
  run_sqlplus_as vpduser_both "$VPDUSER_BOTH_PASSWORD" "$ROOT/sql/adb/10_tests_user_both.sql"
  run_sqlplus_as vpduser_none "$VPDUSER_NONE_PASSWORD" "$ROOT/sql/adb/11_tests_user_none.sql"
  ok "4명 (MY / PG / BOTH / NONE) 테스트 실행 완료"
}

do_audit() {
  log "=== audit: admin 으로 정책 / 뷰 / 매트릭스 점검 ==="
  run_sqlplus_file "$ROOT/sql/adb/12_tests_admin_audit.sql"
  ok "audit 완료"
}

do_teardown() {
  log "=== teardown: ADB 측 객체 + dblink/credential 정리 ==="
  warn "원격 PG/MySQL 의 customers 테이블은 건드리지 않습니다 (수동으로 DROP 하세요)"
  run_sqlplus_file "$ROOT/sql/adb/00_cleanup.sql"
  ok "teardown 완료"
}

# ============================================================
# 디스패치
# ============================================================
case "$CMD" in
  prereq)   do_prereq ;;
  source)   do_prereq; do_source ;;
  adb)      do_prereq; do_adb ;;
  tests)    do_prereq; do_tests ;;
  audit)    do_prereq; do_audit ;;
  teardown) do_prereq; do_teardown ;;
  all)
    do_prereq
    do_source
    do_adb
    do_tests
    do_audit
    ok "=== ALL DONE — VPD POC 전체 파이프라인 통과 ==="
    ;;
  *)
    die "알 수 없는 명령: $CMD  (사용: prereq|source|adb|tests|audit|all|teardown)"
    ;;
esac
