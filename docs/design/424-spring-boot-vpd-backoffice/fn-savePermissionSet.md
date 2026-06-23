# 함수 설계서: `savePermissionSet` (#424)

> **부모 설계서**: ./README.md · **상태**: Draft
> **작성**: [AI] Architect · **구현**: `PermissionService.savePermissionSet` · **테스트**: `PermissionServiceTest`

## 1. 시그니처

```java
PermissionSet savePermissionSet(PermissionSetCommand command)
```

## 2. 책임 (단일 책임, 1줄)

역할과 보호 객체에 대한 행 규칙 및 컬럼 표시 정책을 검증한 뒤 하나의 transaction으로 저장한다.

## 3. 입력

| 파라미터 | 타입 | 제약/검증 | 설명 |
|----------|------|-----------|------|
| `command.roleId` | `long` | 존재하는 역할 | 권한을 받을 역할 |
| `command.objectId` | `long` | 활성 보호 객체 | 조회 대상 객체 |
| `command.action` | `String` | 초기 구현은 `SELECT`만 허용 | 작업 유형 |
| `command.rules` | `List<RuleCommand>` | 1개 이상, 타입별 값 검증 | 행 조건 |
| `command.visibleColumns` | `List<String>` | 보호 객체에 등록된 컬럼만 허용 | 표시 허용 컬럼 |

## 4. 출력

- **반환**: 저장된 `PermissionSet`
- **부수효과**: permission/rule/column mapping 갱신, audit insert

## 5. 동작 / 알고리즘

1. 역할이 존재하는지 확인한다.
2. 보호 객체가 whitelist에 있고 활성 상태인지 확인한다.
3. action이 허용 값인지 확인한다.
4. rule 목록이 비어 있으면 실패한다.
5. rule type별 rule value 형식을 검증한다.
6. 중복 rule을 제거하거나 실패 처리한다. 초기 구현은 실패 처리한다.
7. 표시 컬럼 목록이 보호 객체 컬럼 목록의 부분집합인지 확인한다.
8. transaction 안에서 기존 권한 세트를 갱신한다.
9. audit log를 저장한다.
10. 저장된 권한 세트를 다시 조회해 반환한다.

## 6. 에러 & 실패 모드

| 조건 | 처리 | 반환/예외 |
|------|------|-----------|
| 역할 없음 | 저장 중단 | `RoleNotFoundException` |
| 보호 객체 없음/비활성 | 저장 중단 | `ProtectedObjectNotFoundException` |
| 허용되지 않은 action | 저장 중단 | `InvalidPermissionActionException` |
| rule 없음 | 저장 중단 | `InvalidPermissionRuleException` |
| rule value 형식 오류 | 저장 중단 | `InvalidPermissionRuleException` |
| 컬럼 whitelist 위반 | 저장 중단 | `InvalidColumnPolicyException` |
| audit 저장 실패 | rollback | `DataAccessException` |

## 7. 엣지케이스

- `ALL` rule과 다른 제한 rule이 같이 들어오면 충돌로 보고 실패한다.
- `SELF` rule은 대상 객체에 사용자 식별 컬럼이 등록되어 있어야 허용한다.
- `MY_DEPT` rule은 대상 객체에 부서 컬럼이 등록되어 있어야 허용한다.
- 컬럼 목록이 빈 값이면 민감 컬럼 표시 없음으로 처리한다.

## 8. 복잡도 / 성능

- rule/column 검증은 입력 목록 크기에 대해 O(n)이다.
- 운영자 저장 작업 단위이므로 대량 처리 성능은 주요 병목이 아니다.

## 9. 의존성

- `RoleMapper`
- `ProtectedObjectMapper`
- `PermissionMapper`
- `AuditService`
- `ObjectNamePolicy`

## 10. 테스트 케이스

- [ ] 정상: SELECT 권한과 REGION rule 저장
- [ ] 정상: 민감 컬럼 제외 정책 저장
- [ ] 실패: `ALL`과 `REGION` rule 동시 입력
- [ ] 실패: 비활성 보호 객체 입력
- [ ] 실패: 등록되지 않은 컬럼 입력
- [ ] 실패: audit 저장 실패 시 permission 저장 rollback

## 11. 추적성

- 인수조건: #424의 "내부 사용자, 역할, 사용자-역할, 객체 권한, 행 규칙, 컬럼 표시 권한을 백오피스에서 조회하고 저장할 수 있다."
- 관련 ADR: 없음
