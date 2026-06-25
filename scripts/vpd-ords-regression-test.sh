#!/usr/bin/env bash
# VPD/Redaction ORDS regression test for the backoffice demo objects.
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

if [[ -z "$SQLCL_BIN" || ! -x "$SQLCL_BIN" ]]; then
  echo "[FAIL] SQLcl 실행 파일을 찾을 수 없습니다. SQLCL_BIN=/path/to/sql 로 지정하세요." >&2
  exit 1
fi

if [[ -n "${SQLCL_JAVA_HOME:-}" ]]; then
  export JAVA_HOME="$SQLCL_JAVA_HOME"
fi

DB_USER="${BACKOFFICE_DB_USERNAME:-${ADB_USER:-}}"
DB_PASSWORD="${BACKOFFICE_DB_PASSWORD:-${ADB_PASSWORD:-}}"
DB_TNS="${ADB_TNS:-}"
ORDS_BASE="${BACKOFFICE_ORDS_BASE_URL:-https://yh0olybn5pqce4n-d8aukro81636mon0.adb.ap-seoul-1.oraclecloudapps.com/ords}"
ORDS_PATH="${BACKOFFICE_REGRESSION_ORDS_PATH:-cb-ords/cb-agent-security/vpd/documents}"
DESCRIPTION="codex issue458 regression"
WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/vpd-ords-regression.XXXXXX")"

require_value() {
  local name="$1" value="$2"
  if [[ -z "$value" ]]; then
    echo "[FAIL] $name 값이 필요합니다." >&2
    exit 1
  fi
}

require_value BACKOFFICE_DB_USERNAME "$DB_USER"
require_value BACKOFFICE_DB_PASSWORD "$DB_PASSWORD"
require_value ADB_TNS "$DB_TNS"

TOKEN_HR="codex_hr_$(uuidgen)"
TOKEN_SELF="codex_self_$(uuidgen)"
TOKEN_ALL="codex_all_$(uuidgen)"

run_sqlcl() {
  "$SQLCL_BIN" -s "$DB_USER/$DB_PASSWORD@$DB_TNS"
}

cleanup_tokens() {
  {
    printf "%s\n" "WHENEVER SQLERROR CONTINUE"
    printf "%s\n" "UPDATE cb_agent_bearer_key SET revoked_at = SYSDATE, active = 'N' WHERE description = '$DESCRIPTION';"
    printf "%s\n" "COMMIT;"
    printf "%s\n" "exit"
  } | run_sqlcl >/dev/null || true
}

trap cleanup_tokens EXIT

insert_tokens() {
  {
    printf "%s\n" "WHENEVER SQLERROR EXIT SQL.SQLCODE"
    printf "%s\n" "INSERT INTO cb_agent_bearer_key(key_id, user_id, key_hash, key_prefix, expires_at, active, description) VALUES (cb_agent_bearer_key_seq.NEXTVAL, 101, STANDARD_HASH('$TOKEN_HR', 'SHA256'), 'reg_hr', SYSDATE + (10/1440), 'Y', '$DESCRIPTION');"
    printf "%s\n" "INSERT INTO cb_agent_bearer_key(key_id, user_id, key_hash, key_prefix, expires_at, active, description) VALUES (cb_agent_bearer_key_seq.NEXTVAL, 102, STANDARD_HASH('$TOKEN_SELF', 'SHA256'), 'reg_self', SYSDATE + (10/1440), 'Y', '$DESCRIPTION');"
    printf "%s\n" "INSERT INTO cb_agent_bearer_key(key_id, user_id, key_hash, key_prefix, expires_at, active, description) VALUES (cb_agent_bearer_key_seq.NEXTVAL, 103, STANDARD_HASH('$TOKEN_ALL', 'SHA256'), 'reg_all', SYSDATE + (10/1440), 'Y', '$DESCRIPTION');"
    printf "%s\n" "COMMIT;"
    printf "%s\n" "exit"
  } | run_sqlcl >"$WORK_DIR/token-insert.out"
}

call_and_assert() {
  local label="$1" token="$2" expected_count="$3" expected_ids="$4" expected_contents="$5"
  local response_file="$WORK_DIR/${label}.response.json"
  local request_file="$WORK_DIR/${label}.request.txt"
  local url="${ORDS_BASE%/}/${ORDS_PATH}?limit=50"

  {
    printf "POST %s\n" "$url"
    printf "Authorization: Bearer ****%s\n" "${token: -6}"
    printf "Content-Type: application/json\n"
    printf "{}\n"
  } >"$request_file"

  local status
  status="$(curl -sS -o "$response_file" -w "%{http_code}" \
    -X POST "$url" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -d "{}")"

  python3 - "$label" "$status" "$response_file" "$request_file" "$expected_count" "$expected_ids" "$expected_contents" <<'PY'
import json
import sys

label, status, response_file, request_file, expected_count, expected_ids, expected_contents = sys.argv[1:8]
if status != "200":
    print(f"[FAIL] {label}: HTTP {status}")
    print(f"       request={request_file}")
    print(f"       response={response_file}")
    raise SystemExit(1)

try:
    with open(response_file, encoding="utf-8") as handle:
        data = json.load(handle)
except Exception as exc:
    print(f"[FAIL] {label}: JSON parse failed: {exc}")
    print(f"       request={request_file}")
    print(f"       response={response_file}")
    raise SystemExit(1)

rows = data.get("items", data.get("rows", []))
ids = [row.get("DOC_ID", row.get("doc_id")) for row in rows]
contents_count = sum(1 for row in rows if "CONTENTS" in row or "contents" in row)
expected_ids_list = [int(value) for value in expected_ids.split(",") if value]

failures = []
if len(rows) != int(expected_count):
    failures.append(f"rows expected {expected_count}, actual {len(rows)}")
if ids != expected_ids_list:
    failures.append(f"ids expected {expected_ids_list}, actual {ids}")
if contents_count != int(expected_contents):
    failures.append(f"rows_with_contents expected {expected_contents}, actual {contents_count}")

if failures:
    print(f"[FAIL] {label}: " + "; ".join(failures))
    print(f"       request={request_file}")
    print(f"       response={response_file}")
    raise SystemExit(1)

print(f"[PASS] {label}: rows={len(rows)} ids={ids} rows_with_contents={contents_count}")
PY
}

echo "[INFO] SQLcl: $SQLCL_BIN"
echo "[INFO] ORDS: ${ORDS_BASE%/}/${ORDS_PATH}"
echo "[INFO] Evidence: $WORK_DIR"

insert_tokens
call_and_assert "HR" "$TOKEN_HR" "3" "1,2,6" "0"
call_and_assert "SELF" "$TOKEN_SELF" "1" "3" "0"
call_and_assert "ALL" "$TOKEN_ALL" "6" "1,2,3,4,5,6" "6"

echo "[PASS] VPD/Redaction ORDS regression passed"
