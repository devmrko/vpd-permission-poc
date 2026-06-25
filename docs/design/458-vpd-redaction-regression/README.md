# 설계서: VPD/Redaction 회귀 테스트 매트릭스 자동화 (#458)

> **상태**: Approved
> **작성**: [AI] Architect · **최종수정**: 2026-06-25
> **추적성** — Redmine: #458 · 관련 ADR: 없음
> · 구현 파일: `scripts/vpd-ords-regression-test.sh`, `run.sh` · 테스트: `./run.sh backoffice-vpd-ords-test`, `mvn test`

## 1. 목적 (Why)

VPD filter, ORDS handler, Redaction 설정 변경 후에도 Bearer Token별 조회 행 수와 민감 컬럼 노출 결과가 유지되는지 자동으로 검증한다.

## 2. 범위 (Scope)

- **포함**: 임시 bearer token 생성, ORDS POST 호출, 응답 JSON 검증, token revoke, 실패 증거 파일 저장.
- **제외**: ORDS metadata 생성, VPD policy 생성, UI E2E 브라우저 테스트.

## 3. 인수조건 (Acceptance Criteria)

- [ ] SQLcl로 임시 token을 생성하고 종료 시 revoke한다.
- [ ] HR token은 3행 `[1,2,6]`, `CONTENTS` 미노출을 검증한다.
- [ ] SELF token은 1행 `[3]`, `CONTENTS` 미노출을 검증한다.
- [ ] ALL token은 6행 `[1,2,3,4,5,6]`, `CONTENTS` 노출을 검증한다.
- [ ] 실패 시 request/response 파일 위치를 출력한다.
- [ ] `./run.sh backoffice-vpd-ords-test`로 실행할 수 있다.

## 4. 컨텍스트 & 제약

- SQLcl이 필요하다. `SQLCL_BIN`이 있으면 그 값을 쓰고, 없으면 PATH의 `sql`을 찾는다.
- DB 접속은 `.env`의 `BACKOFFICE_DB_USERNAME`, `BACKOFFICE_DB_PASSWORD`, `ADB_TNS`를 사용한다.
- ORDS base URL은 `BACKOFFICE_ORDS_BASE_URL`이 있으면 쓰고, 없으면 현재 백오피스 기본값을 사용한다.
- token 원문은 출력하지 않는다.

## 5. 아키텍처 개요

```
run.sh backoffice-vpd-ords-test
  -> scripts/vpd-ords-regression-test.sh
      -> SQLcl INSERT temp tokens
      -> curl POST ORDS documents endpoint
      -> Python JSON assertions
      -> SQLcl revoke temp tokens
```

## 6. 데이터 모델

- 테스트 token description: `codex issue458 regression`.
- 사용자 매트릭스:
  - `101`: HR 부서 권한
  - `102`: SELF 권한
  - `103`: ALL 권한 + `CONTENTS` 노출 권한

## 7. 함수 명세 (Function Specs)

| 함수 | 책임(1줄) | 시그니처(잠정) | 입력 | 출력 | 에러/실패 | 복잡? |
|------|-----------|----------------|------|------|-----------|-------|
| `run_sqlcl` | SQLcl에 SQL stdin을 전달 | bash function | SQL text | SQLcl output | SQLcl exit code | 단순 |
| `call_and_assert` | ORDS 호출 후 JSON 결과 검증 | bash function | label, token, expected rows | PASS/FAIL | 실패 시 exit 1 | **복잡** |

## 8. 흐름 / 알고리즘

1. `.env`를 로드한다.
2. SQLcl/JDK/DB/ORDS 환경을 확인한다.
3. UUID 기반 token 3개를 생성한다.
4. SQLcl로 token hash를 DB에 insert한다.
5. ORDS endpoint를 호출해 `items` 행 수, DOC_ID, CONTENTS 노출 수를 검증한다.
6. 성공/실패와 관계없이 token을 revoke한다.

## 9. 엣지케이스 & 에러 처리

- SQLcl 없음: 실행 전 명확한 메시지로 실패.
- ORDS 200 아님: response body 파일 경로 출력.
- JSON parse 실패: response body 일부와 파일 경로 출력.
- 중간 실패: cleanup trap으로 token revoke 시도.

## 10. 테스트 계획

- `SQLCL_BIN=/tmp/sqlcl/sqlcl/bin/sql ./run.sh backoffice-vpd-ords-test`
- `mvn test`

## 11. 리스크 & 대안 검토

- 선택: bash + SQLcl + curl + Python 표준 json. 배포 의존성을 추가하지 않고 운영자가 그대로 실행할 수 있다.
- 대안: JUnit 통합 테스트. Maven에서 외부 ORDS/ADB를 항상 요구하게 되어 로컬 단위 테스트가 불안정해진다.

## 12. 미해결 질문 (Open Questions)

- SQLcl/JDK 경로 자동 탐지는 #460 운영 런북에서 더 정리한다.
