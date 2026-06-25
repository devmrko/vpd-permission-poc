# 설계서: SQLcl 기반 배포/롤백 런북 및 환경 점검 명령 정리 (#460)

> **상태**: Approved
> **작성**: [AI] Architect · **최종수정**: 2026-06-25
> **추적성** — Redmine: #460 · 관련 ADR: 없음
> · 구현 파일: `scripts/check-sqlcl-backoffice-env.sh`, `docs/runbooks/460-sqlcl-vpd-deploy-runbook.md`, `run.sh` · 테스트: `./run.sh backoffice-env-check`

## 1. 목적 (Why)

운영자가 신규 DB/ORDS 환경에서 SQLcl, JDK, ADB 접속, ORDS endpoint를 적용 전에 점검하고 VPD filter 배포/검증/롤백 순서를 확인할 수 있게 한다.

## 2. 범위 (Scope)

- **포함**: SQLcl/JDK/DB/ORDS 환경 점검 스크립트, 배포/검증/롤백 런북, `run.sh backoffice-env-check` 명령.
- **제외**: SQLcl 설치 자동화, JDK 설치 자동화, ORDS metadata 자동 생성.

## 3. 인수조건 (Acceptance Criteria)

- [ ] SQLcl 실행 파일과 버전을 확인한다.
- [ ] Java 버전을 확인한다.
- [ ] DB 접속 사용자와 `CB_AGENT_DOC_VPD_FILTER` 상태를 확인한다.
- [ ] ORDS base URL과 documents endpoint의 HTTP 상태를 확인한다.
- [ ] 운영자가 실행할 배포/검증/롤백 순서가 문서화된다.

## 4. 컨텍스트 & 제약

- `.env`는 민감 정보를 포함하므로 점검 출력에 password/token을 출력하지 않는다.
- SQLcl 경로는 `SQLCL_BIN` 우선, 없으면 PATH의 `sql`을 사용한다.
- JDK는 `SQLCL_JAVA_HOME`이 있으면 `JAVA_HOME`으로 사용한다.

## 5. 아키텍처 개요

```
run.sh backoffice-env-check
  -> scripts/check-sqlcl-backoffice-env.sh
      -> SQLcl version
      -> Java version
      -> SELECT USER, function status
      -> curl ORDS endpoint
```

## 6. 데이터 모델

- 입력 환경변수: `SQLCL_BIN`, `SQLCL_JAVA_HOME`, `BACKOFFICE_DB_USERNAME`, `BACKOFFICE_DB_PASSWORD`, `ADB_TNS`, `BACKOFFICE_ORDS_BASE_URL`.
- 출력: PASS/FAIL 로그, 민감값 마스킹.

## 7. 함수 명세 (Function Specs)

| 함수 | 책임(1줄) | 시그니처(잠정) | 입력 | 출력 | 에러/실패 | 복잡? |
|------|-----------|----------------|------|------|-----------|-------|
| `resolve_sqlcl` | SQLcl 실행 파일 찾기 | bash function | env/path | path | 없으면 exit 1 | 단순 |
| `check_db` | DB 접속과 function 상태 확인 | bash function | SQLcl connection | PASS/FAIL | SQLcl exit code | 단순 |
| `check_ords` | ORDS endpoint HTTP 상태 확인 | bash function | base url/path | PASS/WARN | curl status | 단순 |

## 8. 흐름 / 알고리즘

1. `.env`를 로드한다.
2. SQLcl 경로와 Java 버전을 출력한다.
3. DB 접속 필수값 존재 여부를 확인한다.
4. SQLcl로 현재 사용자와 VPD function 상태를 조회한다.
5. ORDS endpoint에 GET을 보내 상태 코드를 확인한다.
6. 다음 실행 명령을 안내한다.

## 9. 엣지케이스 & 에러 처리

- SQLcl 없음: `SQLCL_BIN=/path/to/sql` 안내.
- DB 접속 실패: DB 설정 확인 메시지.
- ORDS 404: base URL/path 확인 경고.
- ORDS 401/403: endpoint는 살아 있으나 인증 필요 상태로 안내.

## 10. 테스트 계획

- `SQLCL_BIN=/tmp/sqlcl/sqlcl/bin/sql SQLCL_JAVA_HOME=... ./run.sh backoffice-env-check`

## 11. 리스크 & 대안 검토

- 선택: bash 점검 스크립트. 운영자가 Java/Maven 없이도 SQLcl/curl만으로 확인 가능하다.
- 대안: Spring Boot health endpoint. 앱이 뜨기 전 문제를 잡기 어렵다.

## 12. 미해결 질문 (Open Questions)

- SQLcl을 repo 외부 어디에 표준 설치할지는 사용자 로컬/운영 환경 정책에 따른다.
