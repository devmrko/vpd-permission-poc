# 설계서: Spring Boot VPD/ORDS 권한 백오피스 (#424)

> **상태**: Draft
> **작성**: [AI] Architect · **최종수정**: 2026-06-23
> **추적성** — Redmine: #424 · 관련 ADR: 없음
> · 구현 파일: `pom.xml`, `src/main/**`, `src/test/**` · 테스트: `mvn test`

## 1. 목적 (Why)

운영자와 데모 담당자가 ADB VPD/Redaction 권한 매핑, Bearer Token, ORDS 호출 결과를 한 화면에서 관리하고 검증할 수 있는 Spring Boot 백오피스를 만든다.

## 2. 범위 (Scope)

- **포함**:
  - Spring Boot 3.x, Java 21 또는 17, HikariCP, MyBatis 기반 백오피스 골격
  - ADB 권한 관리 테이블과 ORDS Bearer Key 검증 테이블을 조회/수정하는 서비스
  - 보호 객체, 사용자, 역할, 행 규칙, 컬럼 표시 권한, Bearer Token 관리 화면
  - Bearer Token을 사용해 ORDS에 `SELECT *` 성격의 조회를 실행하고 VPD/Redaction 적용 결과를 확인하는 화면
  - Thymeleaf + HTMX + Alpine.js + Bootstrap 5 기반 서버 렌더링 프론트
  - Audit log 저장과 오류 유형 구분 표시
- **제외 (out of scope)**:
  - 외부 SSO 연동
  - 운영 승인 워크플로우
  - DDS DATA GRANT 직접 생성 UI
  - 원격 PostgreSQL/MySQL 데이터 생성 자동화
  - 운영 배포 파이프라인

## 3. 인수조건 (Acceptance Criteria)

- [ ] Spring Boot 애플리케이션이 HikariCP와 MyBatis로 ADB에 연결된다.
- [ ] 보호 객체 목록은 DB에 등록된 whitelist에서만 선택 가능하며, 자유 입력 객체명으로 SQL 또는 ORDS 호출을 만들 수 없다.
- [ ] 내부 사용자, 역할, 사용자-역할, 객체 권한, 행 규칙, 컬럼 표시 권한을 백오피스에서 조회하고 저장할 수 있다.
- [ ] Bearer Token은 발급 시 원문을 한 번만 보여주고 DB에는 hash, prefix, 만료/회수 상태만 저장한다.
- [ ] ORDS 호출 검증 화면에서 Bearer Token과 보호 객체를 선택하면 ORDS 호출 결과, 반환 컬럼, 행 수, 오류 유형을 확인할 수 있다.
- [ ] 권한 없음, 잘못된 토큰, 객체 권한 없음, VPD 0건, Redaction NULL 표시를 서로 구분해 보여준다.
- [ ] 권한 변경, 토큰 발급/회수, ORDS 검증 호출은 audit log에 남는다.
- [ ] `mvn test`로 핵심 서비스 단위 테스트와 Mapper/SQL 검증 테스트를 실행할 수 있다.

## 4. 컨텍스트 & 제약

- 의존성:
  - Oracle Autonomous Database
  - ORDS Handler 또는 REST Enabled SQL 성격의 조회 API
  - Oracle JDBC, MyBatis, Spring Security, Thymeleaf, HTMX
- 제약:
  - `.env`와 DB 비밀번호, Bearer Token 원문은 git에 저장하지 않는다.
  - 객체명은 반드시 DB에 등록된 보호 객체 whitelist로 제한한다.
  - 백오피스 DB 계정은 권한 관리 테이블과 검증용 ORDS 호출에 필요한 최소 권한만 갖는다.
  - VPD는 행(row) 통제, Redaction은 컬럼 값 마스킹/NULL 처리로 설명하고 구현한다.
- 가정:
  - 기존 SQL의 `CB_*` ORDS/VPD Bearer Key 예제를 백오피스 데이터 모델의 기준으로 삼는다.
  - 첫 구현은 단일 관리자 로그인 또는 개발용 in-memory user로 시작하고, 운영 SSO는 후속 범위로 둔다.
  - ORDS 호출은 백오피스 서버에서 수행하며 브라우저에는 Bearer Token 원문을 보존하지 않는다.

## 5. 아키텍처 개요

### 모듈/파일 구조

```text
pom.xml
src/main/java/com/cloudhandson/vpdbackoffice/
  VpdBackofficeApplication.java
  config/
    DataSourceConfig.java
    SecurityConfig.java
    OrdsClientConfig.java
  domain/
    user/
    permission/
    token/
    protectedobject/
    probe/
    audit/
  mapper/
    UserMapper.java
    PermissionMapper.java
    BearerTokenMapper.java
    ProtectedObjectMapper.java
    AuditMapper.java
  service/
    PermissionService.java
    BearerTokenService.java
    OrdsProbeService.java
    AuditService.java
  web/
    PermissionController.java
    TokenController.java
    ProbeController.java
    DashboardController.java
src/main/resources/
  application.yml
  mapper/*.xml
  templates/**/*.html
  static/css/app.css
  static/js/app.js
src/test/java/com/cloudhandson/vpdbackoffice/
```

### 데이터 흐름

```text
관리자 브라우저
  -> Spring MVC Controller
  -> Service
  -> MyBatis Mapper
  -> ADB 권한/토큰/보호 객체 테이블

검증 호출
  -> ProbeController
  -> OrdsProbeService
  -> BearerTokenService에서 token 상태 확인
  -> ProtectedObject whitelist 확인
  -> ORDS HTTP 호출 Authorization: Bearer <token>
  -> 응답/오류 분류
  -> Audit 저장
  -> Thymeleaf fragment로 결과 영역 갱신
```

### I/O와 순수 로직 경계

- Controller는 요청 파라미터 검증과 화면 모델 구성만 담당한다.
- Service는 트랜잭션, 권한 변경, 토큰 발급/회수, ORDS 호출 오케스트레이션을 담당한다.
- Mapper는 SQL I/O만 담당한다.
- 순수 로직은 `TokenGenerator`, `TokenHasher`, `ProbeErrorClassifier`, `ObjectNamePolicy`로 분리해 단위 테스트한다.

## 6. 데이터 모델

### 백오피스 기준 테이블

| 테이블 | 역할 | 주요 컬럼 |
|---|---|---|
| `CB_APP_USER` | 내부 사용자 | `user_id`, `username`, `emp_no`, `dept_code`, `active_yn` |
| `CB_APP_ROLE` | 역할 | `role_id`, `role_name`, `description` |
| `CB_USER_ROLE` | 사용자-역할 매핑 | `user_id`, `role_id` |
| `CB_PROTECTED_OBJECT` | 보호 객체 whitelist | `object_id`, `owner`, `object_name`, `ords_path`, `enabled_yn` |
| `CB_PERMISSION` | 객체 접근 권한 | `permission_id`, `role_id`, `object_id`, `action` |
| `CB_PERMISSION_RULE` | 행 조건 | `rule_id`, `permission_id`, `rule_type`, `rule_value` |
| `CB_PROTECTED_COLUMN` | 컬럼 표시 정책 | `column_id`, `object_id`, `column_name`, `sensitive_yn`, `visible_role_id` |
| `CB_AGENT_BEARER_KEY` | Bearer Token 관리 | `key_id`, `user_id`, `key_prefix`, `key_hash`, `expires_at`, `revoked_at` |
| `CB_ORDS_PROBE_AUDIT` | ORDS 검증 이력 | `audit_id`, `key_id`, `object_id`, `status`, `row_count`, `error_code`, `created_at` |

### 경계 검증 규칙

- `owner`, `object_name`, `column_name`은 Oracle identifier 허용 문자로 검증하되, 검증 후에도 SQL 문자열 직접 조합에 사용하지 않는다.
- ORDS 검증 대상은 `CB_PROTECTED_OBJECT.enabled_yn = 'Y'`인 행만 허용한다.
- Token 원문은 발급 응답 화면에서만 사용하고 DB, log, audit에는 저장하지 않는다.
- 기존 ORDS 패키지가 `STANDARD_HASH(p_bearer_key, 'SHA256')`로 Bearer Key를 검증하므로 `key_hash`는 SHA-256으로 저장한다. HMAC 또는 pepper 적용은 DB 패키지 검증 방식까지 함께 바꾸는 후속 작업으로 둔다.
- `rule_type`은 `ALL`, `MY_DEPT`, `SELF`, `REGION`, `CUSTOM_PREDICATE` 중 허용 값만 저장한다.
- `CUSTOM_PREDICATE`는 초기 구현에서 비활성화한다.

## 7. 함수 명세 (Function Specs)

| 함수 | 책임(1줄) | 시그니처(잠정) | 입력 | 출력 | 에러/실패 | 복잡? |
|------|-----------|----------------|------|------|-----------|-------|
| `issueToken` | Bearer Token을 생성하고 hash만 저장한다 | `IssuedToken issueToken(TokenIssueCommand command)` | 사용자, 만료일, 설명 | prefix와 원문 token | 비활성 사용자, 잘못된 만료일 | **복잡** |
| `revokeToken` | Bearer Token을 회수한다 | `void revokeToken(long keyId, String reason)` | key id, 사유 | 없음 | 없는 key, 이미 회수됨 | 단순 |
| `savePermissionSet` | 역할별 객체 권한과 행/컬럼 규칙을 저장한다 | `PermissionSet savePermissionSet(PermissionSetCommand command)` | 역할, 객체, 규칙 목록 | 저장된 권한 | whitelist 위반, 중복 규칙 | **복잡** |
| `runProbe` | ORDS Bearer 호출을 실행하고 결과를 분류한다 | `ProbeResult runProbe(ProbeCommand command)` | token id, object id, limit | 결과 rows, columns, status | 토큰 만료, ORDS 오류, timeout | **복잡** |
| `classifyProbeError` | ORDS/Oracle 오류를 화면 표시 상태로 분류한다 | `ProbeStatus classifyProbeError(HttpStatus status, String body)` | HTTP 상태, 응답 body | 분류 상태 | 알 수 없는 오류 | 단순 |
| `assertAllowedObject` | 보호 객체 whitelist를 검증한다 | `ProtectedObject assertAllowedObject(long objectId)` | object id | 보호 객체 | 비활성/없는 객체 | 단순 |
| `recordAudit` | 관리 작업과 검증 호출을 audit에 기록한다 | `void recordAudit(AuditEvent event)` | 이벤트 | 없음 | DB insert 실패 | 단순 |

복잡 함수별 상세 설계서는 다음 파일에 둔다.

- `fn-issueToken.md`
- `fn-savePermissionSet.md`
- `fn-runProbe.md`

## 8. 흐름 / 알고리즘

### 권한 저장

1. Controller가 역할, 보호 객체, 행 규칙, 컬럼 표시 정책 입력을 받는다.
2. Service가 role/object 존재와 활성 상태를 확인한다.
3. 행 규칙 타입과 값을 검증한다.
4. 기존 권한 세트를 transaction 안에서 갱신한다.
5. VPD 정책이 참조하는 테이블 구조에 맞춰 권한 매핑을 저장한다.
6. audit log를 남긴다.
7. HTMX fragment로 저장된 권한 요약을 반환한다.

### Token 발급

1. 관리자가 사용자와 만료일을 선택한다.
2. Service가 사용자 활성 상태와 만료일을 검증한다.
3. `vpd_live_` prefix와 충분한 난수 token body를 생성한다.
4. 기존 ORDS 패키지와 호환되는 SHA-256 hash를 만든다.
5. prefix, hash, 만료일만 저장한다.
6. 원문 token은 발급 완료 화면에 한 번만 표시한다.

### ORDS 검증

1. 관리자가 token과 보호 객체를 선택한다.
2. Service가 token 상태와 보호 객체 whitelist를 확인한다.
3. ORDS URL은 DB에 등록된 `ords_path`와 서버 설정의 base URL로만 만든다.
4. `Authorization: Bearer <token>` 헤더로 ORDS를 호출한다.
5. JSON 응답이면 컬럼 목록과 행 수를 계산한다.
6. 오류 응답이면 HTTP 상태와 Oracle 오류 코드를 분류한다.
7. audit log를 남기고 화면에 결과를 표시한다.

## 9. 엣지케이스 & 에러 처리

- Bearer Token 없음: `MISSING_TOKEN`으로 표시하고 ORDS 호출하지 않는다.
- 만료/회수 token: `TOKEN_INACTIVE`로 표시하고 ORDS 호출하지 않는다.
- 잘못된 token hash: `INVALID_TOKEN` 또는 ORDS 응답의 `ORA-20002`로 표시한다.
- 보호 객체 비활성: `OBJECT_DISABLED`로 차단한다.
- 권한 매핑 없음: 호출은 성공하지만 0 rows이면 `VPD_DENY_EMPTY_RESULT`로 설명한다.
- 객체 DB 권한 없음: `ORA-00942`, `ORA-01031`을 `OBJECT_NOT_ACCESSIBLE`로 분류한다.
- Redaction 적용: 민감 컬럼 값이 NULL이면 오류가 아니라 `MASKED_COLUMN` 표시로 보여준다.
- ORDS timeout: 기본 10초 timeout, 재시도 없음. 검증 화면에서 재실행하도록 한다.
- Audit 저장 실패: 권한 변경 작업은 rollback한다. 조회 검증 audit 실패는 오류를 반환한다.

## 10. 테스트 계획

- 단위 테스트:
  - Token 생성 결과가 충분한 길이와 prefix를 갖고 hash만 저장되는지 검증
  - 만료/회수 token 검증 실패
  - 보호 객체 whitelist 위반 차단
  - ORDS 오류 body의 `ORA-20002`, `ORA-00942`, `ORA-01031` 분류
  - 권한 저장 command의 중복/빈 rule 검증
- 통합 테스트:
  - MyBatis Mapper XML 로딩 테스트
  - Testcontainers 사용은 Oracle 제약 때문에 초기 범위에서 제외하고 Mapper SQL은 `@MybatisTest`와 mock datasource 중심으로 검증
  - 실제 ADB 연동 테스트는 `.env`가 있는 개발 환경에서 `mvn test -Pwith-adb`로 분리
- 화면 테스트:
  - Controller slice test로 목록/저장/검증 fragment 렌더링 확인
  - 수동 smoke test로 토큰 발급, 권한 저장, ORDS 검증 실행 확인

## 11. 리스크 & 대안 검토

- 프론트 대안:
  - React/Vue SPA는 화면 표현력은 높지만 빌드 체인과 API 경계가 늘어난다.
  - 이 백오피스는 서버 렌더링 폼, 테이블, 결과 fragment가 중심이므로 Thymeleaf + HTMX + Alpine.js + Bootstrap 5가 가장 단순하다.
- SQL 안전성:
  - 객체명 whitelist 없이 `SELECT * FROM ${table}`을 만들면 SQL injection과 권한 우회 위험이 있다.
  - 따라서 ORDS 호출 대상은 `CB_PROTECTED_OBJECT`의 `ords_path`만 사용한다.
- Token 저장:
  - 평문 저장은 운영 위험이 크다.
  - hash 저장과 one-time display를 기본으로 한다.
- DDS:
  - DDS UI까지 포함하면 범위가 커진다.
  - 현재 요구는 VPD 형태와 ORDS Bearer 검증이 핵심이므로 DDS는 상태 조회/문서 연결만 후속 범위로 둔다.

## 12. 미해결 질문 (Open Questions)

- ORDS 검증 API는 기존 `sql/adb/22_agent_ords_security_ords_handler_setup.sql`의 Handler를 그대로 사용할지, 백오피스 전용 Handler를 추가할지 결정이 필요하다.
- 백오피스 관리자 로그인은 초기에는 local user로 둘지, 사내 인증과 연결할지 후속 결정이 필요하다.
- 컬럼 정책을 Redaction DDL까지 자동 생성할지, 관리 테이블 저장 후 DBA 적용으로 둘지 결정이 필요하다.
- 실제 구현 issue를 별도 Redmine 하위 이슈로 나눌지, #424를 Developer 단계로 계속 이동할지 결정이 필요하다.
