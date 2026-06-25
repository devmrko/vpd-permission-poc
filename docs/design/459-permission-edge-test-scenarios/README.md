# 설계서: 권한 조합/경계값/오류 메시지 시나리오 확장 (#459)

> **상태**: Approved
> **작성**: [AI] Architect · **최종수정**: 2026-06-25
> **추적성** — Redmine: #459 · 관련 ADR: 없음
> · 구현 파일: `docs/testing/459-permission-edge-scenarios.md`, `scripts/vpd-ords-regression-test.sh` · 테스트: `./run.sh backoffice-vpd-ords-test`, `mvn test`

## 1. 목적 (Why)

정상 권한 매트릭스뿐 아니라 잘못된 token, 권한 조합, 컬럼/값 누락, ORDS path 불일치 같은 운영 중 자주 발생할 경계 시나리오를 테스트 항목으로 고정한다.

## 2. 범위 (Scope)

- **포함**: 수동/E2E 테스트 체크리스트 문서, invalid bearer token 자동 smoke, 기대 오류 관점 정리.
- **제외**: Playwright 브라우저 자동화, 모든 UI 입력 케이스 자동화.

## 3. 인수조건 (Acceptance Criteria)

- [ ] 권한 조합/경계값/오류 메시지 체크리스트가 문서화된다.
- [ ] 자동 ORDS smoke에 invalid token 실패 검증이 포함된다.
- [ ] 실패 시 request/response evidence 경로가 출력된다.
- [ ] `mvn test`가 통과한다.

## 4. 컨텍스트 & 제약

- ORDS 정상 매트릭스는 #458 스크립트를 재사용한다.
- UI 입력 검증은 #457에서 일부 보강됐고, 여기서는 테스트 관점에서 확인 항목을 정리한다.

## 5. 아키텍처 개요

```
docs/testing/459-permission-edge-scenarios.md
  -> 수동/운영 테스트 체크리스트

scripts/vpd-ords-regression-test.sh
  -> 정상 HR/SELF/ALL 검증
  -> invalid token smoke 검증
```

## 6. 데이터 모델

- invalid token: DB에 등록하지 않은 UUID token.
- 기대 결과: HTTP 200이 아니어야 하며, 성공 rows로 해석되면 실패.

## 7. 함수 명세 (Function Specs)

| 함수 | 책임(1줄) | 시그니처(잠정) | 입력 | 출력 | 에러/실패 | 복잡? |
|------|-----------|----------------|------|------|-----------|-------|
| `call_and_assert_rejected` | 잘못된 token이 성공 조회로 처리되지 않는지 검증 | bash function | label, token | PASS/FAIL | HTTP 200이면 실패 | 단순 |

## 8. 흐름 / 알고리즘

1. 정상 매트릭스 HR/SELF/ALL을 검증한다.
2. 등록되지 않은 token으로 같은 ORDS endpoint를 호출한다.
3. HTTP 200이면 실패, 아니면 거부 smoke PASS로 처리한다.
4. request/response evidence를 남긴다.

## 9. 엣지케이스 & 에러 처리

- invalid token이 ORDS 403을 반환하는지 확인한다.
- ORDS 404/500도 성공 조회가 아니므로 이 smoke에서는 거부로 본다. 상세 오류 분류는 백오피스 probe classifier에서 검증한다.

## 10. 테스트 계획

- `SQLCL_BIN=/tmp/sqlcl/sqlcl/bin/sql SQLCL_JAVA_HOME=... ./run.sh backoffice-vpd-ords-test`
- `mvn test`

## 11. 리스크 & 대안 검토

- 선택: invalid token smoke를 기존 회귀 스크립트에 포함한다. 테스트 실행 시간이 거의 늘지 않고 보안 기본 동작을 고정할 수 있다.
- 대안: 별도 스크립트. 중복 token/ORDS 설정 로직이 생긴다.

## 12. 미해결 질문 (Open Questions)

- 브라우저 기반 권한 등록/삭제 자동화는 별도 E2E 도입 시 다룬다.
