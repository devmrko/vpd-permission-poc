#!/usr/bin/env bash
# 공용 함수 — log/ok/warn/die, 의존성 검사, 비밀번호 프롬프트
# scripts/lib/common.sh

RED='\033[0;31m'
GRN='\033[0;32m'
YLW='\033[0;33m'
BLU='\033[0;34m'
NC='\033[0m'

log()  { printf "${BLU}[%s]${NC} %s\n" "$(date +%H:%M:%S)" "$*" >&2; }
ok()   { printf "${GRN}[ OK ]${NC} %s\n" "$*" >&2; }
warn() { printf "${YLW}[WARN]${NC} %s\n" "$*" >&2; }
die()  { printf "${RED}[FAIL]${NC} %s\n" "$*" >&2; exit 1; }

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "필수 명령 누락: $1 ($2)"
}

require_env() {
  local missing=()
  for v in "$@"; do
    [[ -z "${!v:-}" ]] && missing+=("$v")
  done
  if (( ${#missing[@]} > 0 )); then
    die ".env 누락 변수: ${missing[*]}"
  fi
}

prompt_password_if_empty() {
  # prompt_password_if_empty VAR_NAME "사람이 읽을 라벨"
  local var="$1" label="$2"
  if [[ -z "${!var:-}" ]]; then
    printf "%s 비밀번호: " "$label" >&2
    read -rs pw
    echo >&2
    [[ -n "$pw" ]] || die "$label 비밀번호가 비어있습니다"
    export "$var=$pw"
  fi
}
