# 함수 설계서: `issueToken` (#424)

> **부모 설계서**: ./README.md · **상태**: Draft
> **작성**: [AI] Architect · **구현**: `BearerTokenService.issueToken` · **테스트**: `BearerTokenServiceTest`

## 1. 시그니처

```java
IssuedToken issueToken(TokenIssueCommand command)
```

## 2. 책임 (단일 책임, 1줄)

활성 사용자에게 새 Bearer Token을 발급하고 DB에는 원문이 아닌 hash와 prefix만 저장한다.

## 3. 입력

| 파라미터 | 타입 | 제약/검증 | 설명 |
|----------|------|-----------|------|
| `command.userId` | `long` | 활성 사용자여야 함 | 토큰 소유 사용자 |
| `command.expiresAt` | `OffsetDateTime` | 현재 시각 이후, 최대 정책 기간 이내 | 만료 시각 |
| `command.description` | `String` | 200자 이하 | 운영자 식별 설명 |

## 4. 출력

- **반환**: `IssuedToken`
  - `keyId`: 저장된 key id
  - `prefix`: 화면 식별용 prefix
  - `plainToken`: 발급 직후 한 번만 보여줄 원문 token
  - `expiresAt`: 만료 시각
- **부수효과**: `CB_AGENT_BEARER_KEY` insert, audit insert

## 5. 동작 / 알고리즘

1. `userId`로 사용자를 조회하고 `active_yn = 'Y'`인지 확인한다.
2. 만료일이 현재 시각 이후인지 확인한다.
3. 정책상 최대 유효 기간을 넘으면 실패한다.
4. `vpd_live_` prefix와 256bit 이상 난수 body를 생성한다.
5. 원문 token을 조합한다.
6. server-side pepper를 읽어 HMAC-SHA256 hash를 만든다.
7. `key_prefix`, `key_hash`, `expires_at`, `created_by`를 저장한다.
8. audit log를 저장한다.
9. 원문 token을 포함한 `IssuedToken`을 반환한다.

## 6. 에러 & 실패 모드

| 조건 | 처리 | 반환/예외 |
|------|------|-----------|
| 사용자가 없음 | 발급 중단 | `UserNotFoundException` |
| 비활성 사용자 | 발급 중단 | `InactiveUserException` |
| 만료일이 과거 | 발급 중단 | `InvalidTokenExpiryException` |
| pepper 설정 없음 | 발급 중단 | `TokenConfigurationException` |
| DB 저장 실패 | transaction rollback | `DataAccessException` |

## 7. 엣지케이스

- 같은 사용자에게 여러 token 발급은 허용한다.
- prefix는 식별용이므로 중복 가능성을 낮추되, 인증 판단에는 hash만 사용한다.
- 원문 token은 log, audit, exception message에 포함하지 않는다.

## 8. 복잡도 / 성능

- 시간 복잡도는 O(1)이다.
- 호출 빈도는 낮고 운영자 작업 단위다.
- 난수 생성은 `SecureRandom` 또는 JDK 보안 API를 사용한다.

## 9. 의존성

- `UserMapper`
- `BearerTokenMapper`
- `AuditService`
- `TokenHasher`
- `Clock`

## 10. 테스트 케이스

- [ ] 정상: 활성 사용자와 미래 만료일 입력 시 원문 token과 저장 id 반환
- [ ] 정상: 저장된 값에 원문 token이 포함되지 않음
- [ ] 실패: 비활성 사용자 입력 시 예외
- [ ] 실패: 과거 만료일 입력 시 예외
- [ ] 실패: pepper 설정 없음

## 11. 추적성

- 인수조건: #424의 "Bearer Token은 발급 시 원문을 한 번만 보여주고 DB에는 hash, prefix, 만료/회수 상태만 저장한다."
- 관련 ADR: 없음
