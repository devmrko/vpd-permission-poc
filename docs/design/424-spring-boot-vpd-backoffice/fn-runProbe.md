# 함수 설계서: `runProbe` (#424)

> **부모 설계서**: ./README.md · **상태**: Draft
> **작성**: [AI] Architect · **구현**: `OrdsProbeService.runProbe` · **테스트**: `OrdsProbeServiceTest`

## 1. 시그니처

```java
ProbeResult runProbe(ProbeCommand command)
```

## 2. 책임 (단일 책임, 1줄)

선택된 Bearer Token과 보호 객체 whitelist를 사용해 ORDS 검증 호출을 실행하고 결과 또는 오류를 분류한다.

## 3. 입력

| 파라미터 | 타입 | 제약/검증 | 설명 |
|----------|------|-----------|------|
| `command.keyId` | `long` | 활성 token이어야 함 | 사용할 Bearer Token |
| `command.objectId` | `long` | 활성 보호 객체 | ORDS 호출 대상 |
| `command.limit` | `int` | 1 이상 500 이하 | 조회 행 제한 |

## 4. 출력

- **반환**: `ProbeResult`
  - `status`: 성공/차단/오류 분류
  - `columns`: 반환 컬럼
  - `rows`: 결과 행
  - `rowCount`: 행 수
  - `maskedColumns`: NULL 또는 마스킹으로 판단된 컬럼
  - `errorCode`, `errorMessage`: 오류 시 분류 정보
- **부수효과**: ORDS HTTP 호출, audit insert

## 5. 동작 / 알고리즘

1. `keyId`로 token 상태를 조회한다.
2. token이 만료 또는 회수 상태면 ORDS 호출 없이 실패 결과를 반환한다.
3. `objectId`로 보호 객체를 조회하고 활성 상태를 확인한다.
4. ORDS URL은 설정된 base URL과 보호 객체의 `ords_path`로 만든다.
5. 요청 timeout과 행 제한 parameter를 적용한다.
6. `Authorization: Bearer <token>` 헤더로 ORDS를 호출한다.
7. 2xx 응답이면 JSON을 파싱해 컬럼과 행 수를 계산한다.
8. 민감 컬럼이 모두 NULL인 경우 Redaction 적용 표시를 만든다.
9. 비 2xx 또는 Oracle 오류 body이면 `classifyProbeError`로 분류한다.
10. audit log를 저장한다.
11. 화면 표시용 `ProbeResult`를 반환한다.

## 6. 에러 & 실패 모드

| 조건 | 처리 | 반환/예외 |
|------|------|-----------|
| token 없음 | ORDS 호출 안 함 | `TOKEN_NOT_FOUND` |
| token 만료/회수 | ORDS 호출 안 함 | `TOKEN_INACTIVE` |
| 보호 객체 없음/비활성 | ORDS 호출 안 함 | `OBJECT_DISABLED` |
| ORDS timeout | audit 후 오류 반환 | `ORDS_TIMEOUT` |
| `ORA-20002` | 잘못된 key로 분류 | `INVALID_TOKEN` |
| `ORA-00942` / `ORA-01031` | 객체 권한 없음으로 분류 | `OBJECT_NOT_ACCESSIBLE` |
| 2xx + 빈 rows | VPD deny 가능성 표시 | `VPD_DENY_EMPTY_RESULT` |
| 알 수 없는 오류 | 원문 일부만 표시 | `UNKNOWN_ERROR` |

## 7. 엣지케이스

- 응답이 JSON이 아니면 성공으로 보지 않고 `INVALID_ORDS_RESPONSE`로 분류한다.
- 결과가 500행을 넘지 않도록 limit 기본값과 최대값을 둔다.
- audit에는 token 원문을 저장하지 않고 `key_id`, `key_prefix`만 저장한다.
- ORDS 응답 body 전체를 audit에 저장하지 않고 오류 코드와 짧은 메시지만 저장한다.

## 8. 복잡도 / 성능

- 네트워크 I/O가 지배적이다.
- 기본 timeout은 10초다.
- 화면 검증 호출이므로 자동 재시도는 하지 않는다.

## 9. 의존성

- `BearerTokenMapper`
- `ProtectedObjectMapper`
- `RestClient` 또는 `WebClient`
- `ProbeErrorClassifier`
- `AuditService`
- `Clock`

## 10. 테스트 케이스

- [ ] 정상: 2xx JSON 응답에서 컬럼과 행 수 추출
- [ ] 정상: 빈 rows를 VPD deny 가능성으로 표시
- [ ] 정상: 민감 컬럼 NULL을 masked column으로 표시
- [ ] 실패: 만료 token이면 ORDS 호출하지 않음
- [ ] 실패: 비활성 객체이면 ORDS 호출하지 않음
- [ ] 실패: `ORA-20002` 분류
- [ ] 실패: timeout 분류

## 11. 추적성

- 인수조건: #424의 "ORDS 호출 검증 화면에서 Bearer Token과 보호 객체를 선택하면 ORDS 호출 결과, 반환 컬럼, 행 수, 오류 유형을 확인할 수 있다."
- 관련 ADR: 없음
