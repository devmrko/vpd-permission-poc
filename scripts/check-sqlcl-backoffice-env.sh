#!/usr/bin/env bash
# Check SQLcl/JDK/ADB/ORDS prerequisites for the VPD backoffice scripts.
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [[ -f "$ROOT/.env" ]]; then
  # shellcheck disable=SC1091
  set -a; . "$ROOT/.env"; set +a
fi

SQLCL_BIN="${SQLCL_BIN:-}"
if [[ -z "$SQLCL_BIN" ]]; then
  SQLCL_BIN="$(command -v sql || true)"
fi

if [[ -n "${SQLCL_JAVA_HOME:-}" ]]; then
  export JAVA_HOME="$SQLCL_JAVA_HOME"
fi

DB_USER="${BACKOFFICE_DB_USERNAME:-${ADB_USER:-}}"
DB_PASSWORD="${BACKOFFICE_DB_PASSWORD:-${ADB_PASSWORD:-}}"
DB_TNS="${ADB_TNS:-}"
ORDS_BASE="${BACKOFFICE_ORDS_BASE_URL:-https://yh0olybn5pqce4n-d8aukro81636mon0.adb.ap-seoul-1.oraclecloudapps.com/ords}"
ORDS_PATH="${BACKOFFICE_REGRESSION_ORDS_PATH:-cb-ords/cb-agent-security/vpd/documents}"

fail() {
  echo "[FAIL] $*" >&2
  exit 1
}

warn() {
  echo "[WARN] $*" >&2
}

pass() {
  echo "[PASS] $*"
}

[[ -n "$SQLCL_BIN" && -x "$SQLCL_BIN" ]] || fail "SQLcl을 찾을 수 없습니다. SQLCL_BIN=/path/to/sql 로 지정하세요."
[[ -n "$DB_USER" ]] || fail "BACKOFFICE_DB_USERNAME 또는 ADB_USER가 필요합니다."
[[ -n "$DB_PASSWORD" ]] || fail "BACKOFFICE_DB_PASSWORD 또는 ADB_PASSWORD가 필요합니다."
[[ -n "$DB_TNS" ]] || fail "ADB_TNS가 필요합니다."

echo "[INFO] SQLcl path: $SQLCL_BIN"
"$SQLCL_BIN" -version

if command -v java >/dev/null 2>&1; then
  java -version 2>&1 | sed -n '1,3p'
else
  warn "java 명령을 PATH에서 찾을 수 없습니다. SQLcl이 자체 Java를 쓰지 않는 환경이면 실패할 수 있습니다."
fi

echo "[INFO] DB user: $DB_USER"
{
  printf "%s\n" "WHENEVER SQLERROR EXIT SQL.SQLCODE"
  printf "%s\n" "SET SQLFORMAT ansiconsole"
  printf "%s\n" "SELECT USER AS current_user FROM dual;"
  printf "%s\n" "SELECT object_name, status FROM user_objects WHERE object_name = 'CB_AGENT_DOC_VPD_FILTER';"
  printf "%s\n" "exit"
} | "$SQLCL_BIN" -s "$DB_USER/$DB_PASSWORD@$DB_TNS"
pass "DB 접속 및 VPD function 상태 조회 완료"

ORDS_URL="${ORDS_BASE%/}/${ORDS_PATH}?limit=1"
STATUS="$(curl -sS -o /tmp/vpd-backoffice-env-check-ords.json -w "%{http_code}" \
  -X POST "$ORDS_URL" \
  -H "Content-Type: application/json" \
  -d "{}" || true)"
case "$STATUS" in
  200|401|403)
    pass "ORDS endpoint 응답 확인: HTTP $STATUS"
    ;;
  404)
    warn "ORDS endpoint가 404입니다. BACKOFFICE_ORDS_BASE_URL과 ORDS path를 확인하세요: $ORDS_URL"
    ;;
  *)
    warn "ORDS endpoint 응답이 예상 밖입니다: HTTP $STATUS ($ORDS_URL)"
    ;;
esac

echo "[INFO] 다음 검증: ./run.sh backoffice-vpd-ords-test"
